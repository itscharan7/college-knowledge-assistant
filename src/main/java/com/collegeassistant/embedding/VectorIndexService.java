package com.collegeassistant.embedding;

import com.collegeassistant.config.AppProperties;
import com.collegeassistant.document.Audience;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The ONLY place that writes to / deletes from the vector store. Business code depends on Spring AI's
 * VectorStore interface, never on a provider class. Embedding happens inside vectorStore.add().
 */
@Service
public class VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public VectorIndexService(VectorStore vectorStore, JdbcTemplate jdbc, ObjectMapper mapper, AppProperties props) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.props = props;
    }

    /** Embeds and stores chunks in small batches, with a short bounded retry on provider errors. */
    public void add(List<Document> chunks) {
        int batch = Math.max(1, props.getAi().getEmbedBatchSize());
        for (int i = 0; i < chunks.size(); i += batch) {
            List<Document> slice = chunks.subList(i, Math.min(chunks.size(), i + batch));
            addWithRetry(slice);
            long delay = props.getAi().getEmbedBatchDelayMs();
            if (delay > 0 && i + batch < chunks.size()) {
                sleep(delay);
            }
        }
    }

    private void addWithRetry(List<Document> slice) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                vectorStore.add(slice);
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("Embedding/storing batch failed (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(1500L * attempt);
                }
            }
        }
        throw last;
    }

    /** Removes every chunk belonging to a document. Safe to call when none exist. */
    public void deleteByDocument(UUID documentId) {
        Filter.Expression filter = new FilterExpressionBuilder().eq("documentId", documentId.toString()).build();
        vectorStore.delete(filter);
    }

    /**
     * Keeps vector metadata in sync when an admin edits title/category/audience, WITHOUT re-embedding.
     * This matters for security: the audience filter used at retrieval time reads this metadata.
     */
    public void updateMetadata(UUID documentId, String title, String category, Audience audience) {
        try {
            String patch = mapper.writeValueAsString(Map.of(
                    "documentTitle", title, "category", category, "audience", audience.name()));
            jdbc.update("UPDATE public.vector_store SET metadata = (metadata::jsonb || ?::jsonb)::json "
                    + "WHERE metadata->>'documentId' = ?", patch, documentId.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not build metadata patch", e);
        }
    }

    public long countForDocument(UUID documentId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM public.vector_store WHERE metadata->>'documentId' = ?",
                Long.class, documentId.toString());
        return n == null ? 0 : n;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
