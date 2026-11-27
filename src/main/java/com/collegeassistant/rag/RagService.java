package com.collegeassistant.rag;

import com.collegeassistant.chat.ChatDtos.AnswerResponse;
import com.collegeassistant.chat.ChatDtos.AnswerResult;
import com.collegeassistant.chat.ChatDtos.CitationDto;
import com.collegeassistant.chat.ConversationService;
import com.collegeassistant.chat.Message;
import com.collegeassistant.chat.MessageRepository;
import com.collegeassistant.chat.MessageRole;
import com.collegeassistant.common.ApiException;
import com.collegeassistant.config.AppProperties;
import com.collegeassistant.rag.AnswerParser.ParsedAnswer;
import com.collegeassistant.retrieval.RetrievalService;
import com.collegeassistant.retrieval.RetrievedChunk;
import com.collegeassistant.user.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * The question-answering pipeline:
 * quota -> (rewrite follow-up) -> permission-filtered retrieval -> [no chunks => refuse, no LLM call]
 * -> prompt -> LLM -> validate (answerable, valid source numbers) -> backend-built citations -> persist.
 *
 * Depends only on Spring AI's ChatModel interface, so the provider is swappable by configuration.
 * (Not ChatClient's .system()/.user() text API: it treats {braces} as template variables, and PDF text
 * can contain braces.) Deliberately not @Transactional: no DB transaction is held during the LLM call.
 */
@Service
public class RagService {

    public static final String NOT_FOUND_MESSAGE = "I couldn't find this information in the available college "
            + "documents. Please contact the relevant department or ask an admin to upload the document that "
            + "contains it.";

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RetrievalService retrieval;
    private final ChatModel chatModel;
    private final PromptFactory prompts;
    private final AnswerParser parser;
    private final ConversationService conversations;
    private final MessageRepository messages;
    private final AppProperties props;

    public RagService(RetrievalService retrieval, ChatModel chatModel, PromptFactory prompts, AnswerParser parser,
                      ConversationService conversations, MessageRepository messages, AppProperties props) {
        this.retrieval = retrieval;
        this.chatModel = chatModel;
        this.prompts = prompts;
        this.parser = parser;
        this.conversations = conversations;
        this.messages = messages;
        this.props = props;
    }

    public AnswerResponse ask(Long userId, Role role, UUID conversationId, String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        if (question.isEmpty()) {
            throw ApiException.badRequest("Please type a question.");
        }
        if (question.length() > props.getAi().getMaxQuestionLength()) {
            throw ApiException.badRequest("Question is too long (max " + props.getAi().getMaxQuestionLength()
                    + " characters).");
        }
        conversations.getOwned(userId, conversationId); // ownership check, 404 otherwise
        enforceQuota(userId);

        long started = System.nanoTime();
        List<Message> history = conversations.recent(conversationId, props.getAi().getHistoryMessages());
        String searchQuery = rewriteIfNeeded(question, history);

        List<RetrievedChunk> chunks = retrieval.retrieve(searchQuery, role);
        AnswerResult result;
        if (chunks.isEmpty()) {
            result = notFound(null, started); // layer 1: deterministic refusal, no LLM call
        } else {
            result = generate(searchQuery, chunks, started);
        }

        Long messageId = conversations.saveTurn(conversationId, userId, question, result);
        return new AnswerResponse(conversationId, messageId, result.answer(), result.answerable(),
                result.citations());
    }

    private AnswerResult generate(String question, List<RetrievedChunk> chunks, long started) {
        String raw = callModel(prompts.answerSystem(), prompts.answerUser(question, chunks));
        double top = chunks.stream().mapToDouble(RetrievedChunk::score).max().orElse(0);

        Optional<ParsedAnswer> parsed = parser.parse(raw);
        if (parsed.isEmpty() || !parsed.get().answerable() || parsed.get().answer().isBlank()) {
            log.info("Model declined or returned unparseable output; treating as not found");
            return notFound(top, started); // layer 2: the model said the context lacks the answer
        }
        // Citation integrity: only source numbers we actually supplied are accepted.
        List<Integer> valid = parsed.get().sources().stream()
                .filter(n -> n >= 1 && n <= chunks.size()).distinct().toList();
        if (valid.isEmpty()) {
            log.info("Answer had no valid source numbers; treating as not found");
            return notFound(top, started);
        }
        List<CitationDto> citations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int n : valid) {
            RetrievedChunk c = chunks.get(n - 1);
            if (seen.add(c.documentId() + "#" + c.pageNumber())) {
                citations.add(new CitationDto(c.documentId(), c.documentTitle(), c.filename(), c.pageNumber(),
                        c.chunkId(), excerpt(c.text()), c.score()));
            }
        }
        return new AnswerResult(true, parsed.get().answer(), citations, top, elapsedMs(started));
    }

    private AnswerResult notFound(Double topScore, long started) {
        return new AnswerResult(false, NOT_FOUND_MESSAGE, List.of(), topScore, elapsedMs(started));
    }

    /** One extra LLM call, only for follow-ups. Falls back to the original question on any problem. */
    private String rewriteIfNeeded(String question, List<Message> history) {
        if (!props.getAi().isRewriteEnabled() || history.isEmpty()) {
            return question;
        }
        try {
            StringBuilder h = new StringBuilder();
            for (Message m : history) {
                String text = m.getContent().length() > 300 ? m.getContent().substring(0, 300) + "…"
                        : m.getContent();
                h.append(m.getRole() == MessageRole.USER ? "User: " : "Assistant: ").append(text).append('\n');
            }
            String rewritten = callModel(prompts.rewriteSystem(), prompts.rewriteUser(h.toString(), question)).strip();
            if (rewritten.isEmpty() || rewritten.length() > props.getAi().getMaxQuestionLength() * 2) {
                return question;
            }
            return rewritten;
        } catch (RuntimeException e) {
            log.warn("Query rewrite failed, using the original question: {}", e.getMessage());
            return question;
        }
    }

    private String callModel(String system, String user) {
        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(new SystemMessage(system),
                    new UserMessage(user))));
            String text = response.getResult().getOutput().getText();
            return text == null ? "" : text;
        } catch (RuntimeException e) {
            log.error("LLM call failed", e);
            throw ApiException.unavailable("The AI service is busy or unavailable right now. Please try again in "
                    + "a moment.");
        }
    }

    private void enforceQuota(Long userId) {
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        long used = messages.countUserQuestionsSince(userId, start);
        if (used >= props.getAi().getDailyQuota()) {
            throw ApiException.tooManyRequests("You have reached today's question limit ("
                    + props.getAi().getDailyQuota() + "). Please try again tomorrow.");
        }
    }

    private String excerpt(String text) {
        int max = props.getAi().getExcerptChars();
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private static int elapsedMs(long started) {
        return (int) ((System.nanoTime() - started) / 1_000_000);
    }
}
