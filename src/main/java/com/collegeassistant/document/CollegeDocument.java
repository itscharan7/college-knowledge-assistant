package com.collegeassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Named CollegeDocument to avoid clashing with Spring AI's org.springframework.ai.document.Document. */
@Entity
@Table(name = "documents")
@Getter
@Setter
public class CollegeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Audience audience;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "storage_key", nullable = false, length = 100)
    private String storageKey;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private DocStatus status = DocStatus.UPLOADED;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
