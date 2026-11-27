package com.collegeassistant.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question) {
    }

    public record ConversationDto(UUID id, String title, Instant updatedAt) {
        public static ConversationDto from(Conversation c) {
            return new ConversationDto(c.getId(), c.getTitle(), c.getUpdatedAt());
        }
    }

    /** documentId is null when the cited document has since been deleted. */
    public record CitationDto(UUID documentId, String documentTitle, String filename, int page, String chunkRef,
                              String excerpt, Double score) {
    }

    public record MessageDto(Long id, MessageRole role, String content, Boolean answerable, Instant createdAt,
                             List<CitationDto> sources) {
    }

    public record AnswerResponse(UUID conversationId, Long messageId, String answer, boolean answerable,
                                 List<CitationDto> sources) {
    }

    /** Internal result of the RAG pipeline, before persistence. */
    public record AnswerResult(boolean answerable, String answer, List<CitationDto> citations, Double topScore,
                               int latencyMs) {
    }
}
