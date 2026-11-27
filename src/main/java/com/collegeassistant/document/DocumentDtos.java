package com.collegeassistant.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** Full view, admins only. */
    public record DocumentDto(UUID id, String title, String originalFilename, String category, Audience audience,
                              String description, long fileSize, int pageCount, int chunkCount, DocStatus status,
                              String processingError, int version, Long uploadedBy, Instant createdAt,
                              Instant updatedAt) {
        public static DocumentDto from(CollegeDocument d) {
            return new DocumentDto(d.getId(), d.getTitle(), d.getOriginalFilename(), d.getCategory(),
                    d.getAudience(), d.getDescription(), d.getFileSize(), d.getPageCount(), d.getChunkCount(),
                    d.getStatus(), d.getProcessingError(), d.getVersion(), d.getUploadedBy(), d.getCreatedAt(),
                    d.getUpdatedAt());
        }
    }

    /** Minimal view for students/faculty: no errors, checksums, paths or uploader. */
    public record DocumentSummary(UUID id, String title, String category, int pageCount, Instant updatedAt) {
        public static DocumentSummary from(CollegeDocument d) {
            return new DocumentSummary(d.getId(), d.getTitle(), d.getCategory(), d.getPageCount(), d.getUpdatedAt());
        }
    }

    public record LogDto(String stage, String status, String message, Instant createdAt) {
        public static LogDto from(ProcessingLog l) {
            return new LogDto(l.getStage(), l.getStatus(), l.getMessage(), l.getCreatedAt());
        }
    }

    public record StatusDto(UUID id, DocStatus status, String processingError, int pageCount, int chunkCount,
                            List<LogDto> logs) {
    }

    public record UpdateDocumentRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank String category,
            @NotNull Audience audience,
            @Size(max = 2000) String description) {
    }
}
