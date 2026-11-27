package com.collegeassistant.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One citation attached to an assistant message. document_title is stored so history survives document deletion. */
@Entity
@Table(name = "message_sources")
@Getter
@Setter
public class MessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_title", nullable = false)
    private String documentTitle;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "chunk_reference", length = 64)
    private String chunkReference;

    @Column(columnDefinition = "text")
    private String excerpt;

    private Double score;
}
