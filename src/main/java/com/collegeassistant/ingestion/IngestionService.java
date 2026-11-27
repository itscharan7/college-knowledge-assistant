package com.collegeassistant.ingestion;

import com.collegeassistant.config.AppProperties;
import com.collegeassistant.document.CollegeDocument;
import com.collegeassistant.document.DocStatus;
import com.collegeassistant.document.DocumentRepository;
import com.collegeassistant.document.ProcessingLogger;
import com.collegeassistant.embedding.VectorIndexService;
import com.collegeassistant.storage.FileStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the indexing pipeline: extract -> clean (+tables) -> chunk -> embed+store -> READY.
 * Runs asynchronously on a small bounded executor; the upload request returns immediately (202).
 * On any failure the document is marked FAILED, partial vectors are removed and the reason is logged.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository docs;
    private final FileStorageService storage;
    private final PdfExtractor extractor;
    private final TextCleaner cleaner;
    private final Chunker chunker;
    private final VectorIndexService vectors;
    private final ProcessingLogger logger;
    private final AppProperties props;

    public IngestionService(DocumentRepository docs, FileStorageService storage, PdfExtractor extractor,
                            TextCleaner cleaner, Chunker chunker, VectorIndexService vectors,
                            ProcessingLogger logger, AppProperties props) {
        this.docs = docs;
        this.storage = storage;
        this.extractor = extractor;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.vectors = vectors;
        this.logger = logger;
        this.props = props;
    }

    @Async("ingestionExecutor")
    public void process(UUID documentId) {
        CollegeDocument doc = docs.findById(documentId).orElse(null);
        if (doc == null) {
            return;
        }
        doc.setStatus(DocStatus.PROCESSING);
        doc.setProcessingError(null);
        docs.save(doc);
        logger.info(documentId, "PROCESSING", "Processing started (version " + doc.getVersion() + ")");

        try {
            byte[] bytes;
            try (InputStream in = storage.open(doc.getStorageKey())) {
                bytes = in.readAllBytes();
            }

            PdfExtractor.Extraction extraction = extractor.extract(bytes);
            doc.setPageCount(extraction.pageCount());
            logger.ok(documentId, "EXTRACT", "Extracted text from " + extraction.pageCount() + " pages");

            TextCleaner.Cleaned cleaned = cleaner.clean(extraction.pages());
            if (cleaned.totalChars() < props.getIngestion().getMinTextChars()) {
                throw new IngestionException("Almost no text could be extracted. This looks like a scanned/image "
                        + "PDF; OCR is not supported.");
            }
            logger.ok(documentId, "CLEAN", "Kept " + cleaned.pages().size() + " pages with text; tables detected: "
                    + cleaned.tablesFound() + (cleaned.tablesFound() == 0
                    ? " (plain-text extraction used; table detection is a heuristic)" : ""));

            List<Chunk> chunks = chunker.chunk(cleaned.pages());
            if (chunks.isEmpty()) {
                throw new IngestionException("No text chunks were produced from this document.");
            }
            logger.ok(documentId, "CHUNK", "Created " + chunks.size() + " chunks");

            vectors.deleteByDocument(documentId); // clear previous version / partial leftovers
            vectors.add(toVectorDocuments(doc, chunks));
            logger.ok(documentId, "EMBED", "Embedded and stored " + chunks.size() + " chunks");

            doc.setChunkCount(chunks.size());
            doc.setStatus(DocStatus.READY);
            docs.save(doc);
            logger.ok(documentId, "READY", "Document is ready for questions");
        } catch (Exception e) {
            fail(doc, e);
        }
    }

    private List<org.springframework.ai.document.Document> toVectorDocuments(CollegeDocument doc, List<Chunk> chunks) {
        List<org.springframework.ai.document.Document> out = new ArrayList<>();
        for (Chunk c : chunks) {
            String chunkId = UUID.randomUUID().toString();
            Map<String, Object> meta = new HashMap<>();
            meta.put("documentId", doc.getId().toString());
            meta.put("chunkId", chunkId);
            meta.put("chunkIndex", c.chunkIndex());
            meta.put("documentTitle", doc.getTitle());
            meta.put("originalFilename", doc.getOriginalFilename());
            meta.put("category", doc.getCategory());
            meta.put("audience", doc.getAudience().name());
            meta.put("pageNumber", c.pageNumber());
            meta.put("documentVersion", doc.getVersion());
            out.add(new org.springframework.ai.document.Document(chunkId, c.text(), meta));
        }
        return out;
    }

    private void fail(CollegeDocument doc, Exception e) {
        log.error("Processing failed for document {}", doc.getId(), e);
        String message = friendly(e);
        try {
            vectors.deleteByDocument(doc.getId()); // never leave partial vectors behind
        } catch (RuntimeException cleanup) {
            log.warn("Cleanup of partial vectors failed for {}: {}", doc.getId(), cleanup.getMessage());
        }
        doc.setChunkCount(0);
        doc.setStatus(DocStatus.FAILED);
        doc.setProcessingError(message);
        docs.save(doc);
        logger.error(doc.getId(), "FAILED", message);
    }

    private String friendly(Exception e) {
        if (e instanceof IngestionException) {
            return e.getMessage();
        }
        if (e instanceof IOException) {
            return "The PDF could not be read: " + shorten(e.getMessage());
        }
        return "Processing failed (" + e.getClass().getSimpleName() + "): " + shorten(e.getMessage())
                + ". If this mentions the embedding service or rate limits, wait and use Retry.";
    }

    private String shorten(String s) {
        if (s == null) {
            return "no details";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** After a crash/restart, documents stuck in PROCESSING would never finish; mark them FAILED so admin can retry. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (CollegeDocument d : docs.findByStatus(DocStatus.PROCESSING)) {
            d.setStatus(DocStatus.FAILED);
            d.setProcessingError("Processing was interrupted by an application restart. Use Retry.");
            docs.save(d);
            logger.error(d.getId(), "FAILED", d.getProcessingError());
        }
    }
}
