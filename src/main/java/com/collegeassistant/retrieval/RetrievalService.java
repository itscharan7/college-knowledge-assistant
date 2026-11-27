package com.collegeassistant.retrieval;

import com.collegeassistant.config.AppProperties;
import com.collegeassistant.document.AccessPolicy;
import com.collegeassistant.document.CollegeDocument;
import com.collegeassistant.document.DocStatus;
import com.collegeassistant.document.DocumentRepository;
import com.collegeassistant.user.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * Semantic search with permission filtering done BEFORE anything reaches the LLM:
 * 1) a metadata filter on "audience" is part of the vector query itself;
 * 2) as defence in depth, each hit is re-checked against the documents table (exists, READY, role may view).
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final DocumentRepository docs;
    private final AppProperties props;

    public RetrievalService(VectorStore vectorStore, DocumentRepository docs, AppProperties props) {
        this.vectorStore = vectorStore;
        this.docs = docs;
        this.props = props;
    }

    public List<RetrievedChunk> retrieve(String query, Role role) {
        AppProperties.Ai ai = props.getAi();
        Object[] audiences = AccessPolicy.allowedAudiences(role).stream().map(Enum::name).toArray();
        Filter.Expression filter = new FilterExpressionBuilder().in("audience", audiences).build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(ai.getTopK())
                .similarityThreshold(ai.getSimilarityThreshold())
                .filterExpression(filter)
                .build();
        List<Document> hits = vectorStore.similaritySearch(request);
        if (hits == null || hits.isEmpty()) {
            log.debug("Retrieval: no chunks above threshold {} for role {}", ai.getSimilarityThreshold(), role);
            return List.of();
        }

        Set<UUID> ids = hits.stream().map(h -> UUID.fromString(String.valueOf(h.getMetadata().get("documentId"))))
                .collect(Collectors.toSet());
        Map<UUID, CollegeDocument> byId = docs.findAllById(ids).stream()
                .collect(Collectors.toMap(CollegeDocument::getId, Function.identity()));

        List<RetrievedChunk> out = new ArrayList<>();
        for (Document h : hits) {
            Map<String, Object> meta = h.getMetadata();
            UUID docId = UUID.fromString(String.valueOf(meta.get("documentId")));
            CollegeDocument d = byId.get(docId);
            if (d == null || d.getStatus() != DocStatus.READY || !AccessPolicy.canView(role, d.getAudience())) {
                continue;
            }
            int page = ((Number) meta.get("pageNumber")).intValue();
            double score = h.getScore() == null ? 0.0 : h.getScore();
            out.add(new RetrievedChunk(docId, d.getTitle(), d.getOriginalFilename(), page,
                    String.valueOf(meta.get("chunkId")), h.getText(), score));
        }
        if (log.isDebugEnabled()) {
            out.forEach(c -> log.debug("Retrieved score={} doc='{}' page={}", String.format("%.3f", c.score()),
                    c.documentTitle(), c.pageNumber()));
        }
        return out;
    }
}
