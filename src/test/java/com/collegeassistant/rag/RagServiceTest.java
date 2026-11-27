package com.collegeassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeassistant.chat.ChatDtos.AnswerResponse;
import com.collegeassistant.chat.ChatDtos.AnswerResult;
import com.collegeassistant.chat.ConversationService;
import com.collegeassistant.chat.MessageRepository;
import com.collegeassistant.common.ApiException;
import com.collegeassistant.config.AppProperties;
import com.collegeassistant.retrieval.RetrievalService;
import com.collegeassistant.retrieval.RetrievedChunk;
import com.collegeassistant.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class RagServiceTest {

    private final UUID doc = UUID.randomUUID();
    private final UUID conv = UUID.randomUUID();
    private RetrievalService retrieval;
    private ChatModel chatModel;
    private ConversationService conversations;
    private MessageRepository messages;
    private AppProperties props;
    private RagService rag;

    @BeforeEach
    void setUp() {
        retrieval = mock(RetrievalService.class);
        chatModel = mock(ChatModel.class);
        conversations = mock(ConversationService.class);
        messages = mock(MessageRepository.class);
        props = new AppProperties();
        when(conversations.recent(any(), anyInt())).thenReturn(List.of());
        when(conversations.saveTurn(any(), anyLong(), any(), any())).thenReturn(99L);
        rag = new RagService(retrieval, chatModel, new PromptFactory(), new AnswerParser(new ObjectMapper()),
                conversations, messages, props);
    }

    private RetrievedChunk chunk(int page) {
        return new RetrievedChunk(doc, "Academic Regulations", "reg.pdf", page, "c" + page,
                "Students need 75% attendance.", 0.82);
    }

    private void modelReplies(String json) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    @Test
    void noRetrievedChunksMeansRefusalWithoutCallingTheModel() {
        when(retrieval.retrieve(any(), eq(Role.STUDENT))).thenReturn(List.of());

        AnswerResponse r = rag.ask(1L, Role.STUDENT, conv, "What is the hostel mess menu?");

        assertThat(r.answerable()).isFalse();
        assertThat(r.answer()).isEqualTo(RagService.NOT_FOUND_MESSAGE);
        assertThat(r.sources()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void validAnswerGetsBackendBuiltCitations() {
        when(retrieval.retrieve(any(), eq(Role.STUDENT))).thenReturn(List.of(chunk(14), chunk(15)));
        modelReplies("{\"answerable\": true, \"answer\": \"You need 75%.\", \"sources\": [2]}");

        AnswerResponse r = rag.ask(1L, Role.STUDENT, conv, "Minimum attendance?");

        assertThat(r.answerable()).isTrue();
        assertThat(r.sources()).hasSize(1);
        assertThat(r.sources().get(0).page()).isEqualTo(15); // source [2] -> page 15, from retrieval metadata
        assertThat(r.sources().get(0).documentId()).isEqualTo(doc);
    }

    @Test
    void inventedSourceNumbersAreRejectedAsNotFound() {
        when(retrieval.retrieve(any(), eq(Role.STUDENT))).thenReturn(List.of(chunk(14)));
        modelReplies("{\"answerable\": true, \"answer\": \"You need 75%.\", \"sources\": [7]}");

        AnswerResponse r = rag.ask(1L, Role.STUDENT, conv, "Minimum attendance?");

        assertThat(r.answerable()).isFalse();
        assertThat(r.sources()).isEmpty();
    }

    @Test
    void modelSayingNotAnswerableIsRespected() {
        when(retrieval.retrieve(any(), eq(Role.STUDENT))).thenReturn(List.of(chunk(14)));
        modelReplies("{\"answerable\": false, \"answer\": \"\", \"sources\": []}");

        assertThat(rag.ask(1L, Role.STUDENT, conv, "Who won the IPL?").answerable()).isFalse();
    }

    @Test
    void unparseableModelOutputIsNeverShownAsAnAnswer() {
        when(retrieval.retrieve(any(), eq(Role.STUDENT))).thenReturn(List.of(chunk(14)));
        modelReplies("The attendance is probably around 75 percent.");

        AnswerResponse r = rag.ask(1L, Role.STUDENT, conv, "Minimum attendance?");
        assertThat(r.answerable()).isFalse();
        assertThat(r.answer()).doesNotContain("probably");
    }

    @Test
    void persistsTheTurnWithTheComputedResult() {
        when(retrieval.retrieve(any(), eq(Role.FACULTY))).thenReturn(List.of(chunk(3)));
        modelReplies("{\"answerable\": true, \"answer\": \"Yes.\", \"sources\": [1]}");

        rag.ask(5L, Role.FACULTY, conv, "Is it allowed?");

        ArgumentCaptor<AnswerResult> captor = ArgumentCaptor.forClass(AnswerResult.class);
        verify(conversations).saveTurn(eq(conv), eq(5L), eq("Is it allowed?"), captor.capture());
        assertThat(captor.getValue().answerable()).isTrue();
    }

    @Test
    void llmFailureBecomes503AndNothingIsSaved() {
        when(retrieval.retrieve(any(), eq(Role.STUDENT))).thenReturn(List.of(chunk(14)));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("rate limited"));

        assertThatThrownBy(() -> rag.ask(1L, Role.STUDENT, conv, "Minimum attendance?"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("busy or unavailable");
        verify(conversations, never()).saveTurn(any(), anyLong(), any(), any());
    }

    @Test
    void dailyQuotaIsEnforced() {
        props.getAi().setDailyQuota(2);
        when(messages.countUserQuestionsSince(eq(1L), any())).thenReturn(2L);

        assertThatThrownBy(() -> rag.ask(1L, Role.STUDENT, conv, "Anything?"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("limit");
        verify(retrieval, never()).retrieve(any(), any());
    }

    @Test
    void overlongQuestionIsRejected() {
        assertThatThrownBy(() -> rag.ask(1L, Role.STUDENT, conv, "x".repeat(501)))
                .isInstanceOf(ApiException.class);
    }
}
