package com.collegeassistant.chat;

import com.collegeassistant.chat.ChatDtos.AnswerResult;
import com.collegeassistant.chat.ChatDtos.CitationDto;
import com.collegeassistant.chat.ChatDtos.ConversationDto;
import com.collegeassistant.chat.ChatDtos.MessageDto;
import com.collegeassistant.common.ApiException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conversations and messages. Every read/write is scoped to the owning user; others get "not found". */
@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageSourceRepository sources;
    private final UnansweredQuestionRepository unanswered;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               MessageSourceRepository sources, UnansweredQuestionRepository unanswered) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.unanswered = unanswered;
    }

    @Transactional
    public ConversationDto create(Long userId) {
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setUpdatedAt(Instant.now());
        return ConversationDto.from(conversations.save(c));
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> list(Long userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(ConversationDto::from).toList();
    }

    /** Returns the conversation only if it belongs to the user; otherwise 404 (does not reveal that it exists). */
    @Transactional(readOnly = true)
    public Conversation getOwned(Long userId, UUID conversationId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found."));
    }

    @Transactional(readOnly = true)
    public List<MessageDto> messages(Long userId, UUID conversationId) {
        getOwned(userId, conversationId);
        List<Message> msgs = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
        return toDtos(msgs);
    }

    /** The most recent messages, oldest first, for query rewriting. */
    @Transactional(readOnly = true)
    public List<Message> recent(UUID conversationId, int limit) {
        List<Message> latest = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId,
                PageRequest.of(0, limit));
        Collections.reverse(latest);
        return latest;
    }

    @Transactional
    public void delete(Long userId, UUID conversationId) {
        Conversation c = getOwned(userId, conversationId);
        conversations.delete(c); // messages, sources and feedback are removed by ON DELETE CASCADE
    }

    /** Saves the question and the answer together, plus its citations and (if unanswered) the admin log entry. */
    @Transactional
    public Long saveTurn(UUID conversationId, Long userId, String question, AnswerResult result) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found."));

        Message q = new Message();
        q.setConversationId(conversationId);
        q.setRole(MessageRole.USER);
        q.setContent(question);
        messages.save(q);

        Message a = new Message();
        a.setConversationId(conversationId);
        a.setRole(MessageRole.ASSISTANT);
        a.setContent(result.answer());
        a.setAnswerable(result.answerable());
        a.setTopScore(result.topScore());
        a.setLatencyMs(result.latencyMs());
        a = messages.save(a);

        for (CitationDto cit : result.citations()) {
            MessageSource s = new MessageSource();
            s.setMessageId(a.getId());
            s.setDocumentId(cit.documentId());
            s.setDocumentTitle(cit.documentTitle());
            s.setPageNumber(cit.page());
            s.setChunkReference(cit.chunkRef());
            s.setExcerpt(cit.excerpt());
            s.setScore(cit.score());
            sources.save(s);
        }

        if (!result.answerable()) {
            UnansweredQuestion u = new UnansweredQuestion();
            u.setUserId(userId);
            u.setQuestion(question);
            unanswered.save(u);
        }

        if (Conversation.DEFAULT_TITLE.equals(c.getTitle())) {
            c.setTitle(question.length() > 60 ? question.substring(0, 60) + "…" : question);
        }
        c.setUpdatedAt(Instant.now());
        conversations.save(c);
        return a.getId();
    }

    private List<MessageDto> toDtos(List<Message> msgs) {
        Collection<Long> ids = msgs.stream().map(Message::getId).toList();
        Map<Long, List<CitationDto>> byMessage = new HashMap<>();
        if (!ids.isEmpty()) {
            for (MessageSource s : sources.findByMessageIdIn(ids)) {
                byMessage.computeIfAbsent(s.getMessageId(), k -> new java.util.ArrayList<>())
                        .add(new CitationDto(s.getDocumentId(), s.getDocumentTitle(), null, s.getPageNumber(),
                                s.getChunkReference(), s.getExcerpt(), s.getScore()));
            }
        }
        return msgs.stream().map(m -> new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getAnswerable(),
                m.getCreatedAt(), byMessage.getOrDefault(m.getId(), List.of()))).collect(Collectors.toList());
    }
}
