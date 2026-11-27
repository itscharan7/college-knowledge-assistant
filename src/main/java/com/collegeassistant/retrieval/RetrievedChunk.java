package com.collegeassistant.retrieval;

import java.util.UUID;

/** A chunk returned by similarity search, already permission-checked. Citation data comes from here, not from the LLM. */
public record RetrievedChunk(UUID documentId, String documentTitle, String filename, int pageNumber,
                             String chunkId, String text, double score) {
}
