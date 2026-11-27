package com.collegeassistant.document;

import com.collegeassistant.common.ApiException;
import com.collegeassistant.config.AppProperties;
import com.collegeassistant.document.DocumentDtos.DocumentDto;
import com.collegeassistant.document.DocumentDtos.DocumentSummary;
import com.collegeassistant.document.DocumentDtos.LogDto;
import com.collegeassistant.document.DocumentDtos.StatusDto;
import com.collegeassistant.document.DocumentDtos.UpdateDocumentRequest;
import com.collegeassistant.embedding.VectorIndexService;
import com.collegeassistant.ingestion.IngestionService;
import com.collegeassistant.storage.FileStorageService;
import com.collegeassistant.user.Role;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document lifecycle. Deliberately NOT @Transactional at class level: each repository call commits on its
 * own, so the async ingestion thread always sees the committed row.
 */
@Service
public class DocumentService {

    private final DocumentRepository docs;
    private final ProcessingLogRepository logs;
    private final ProcessingLogger logger;
    private final FileStorageService storage;
    private final PdfValidator validator;
    private final IngestionService ingestion;
    private final VectorIndexService vectors;
    private final AppProperties props;

    public DocumentService(DocumentRepository docs, ProcessingLogRepository logs, ProcessingLogger logger,
                           FileStorageService storage, PdfValidator validator, IngestionService ingestion,
                           VectorIndexService vectors, AppProperties props) {
        this.docs = docs;
        this.logs = logs;
        this.logger = logger;
        this.storage = storage;
        this.validator = validator;
        this.ingestion = ingestion;
        this.vectors = vectors;
        this.props = props;
    }

    // ---------- admin operations ----------

    public DocumentDto upload(MultipartFile file, String title, String category, Audience audience,
                              String description, Long userId) {
        validateCategory(category);
        byte[] bytes = read(file);
        validator.validate(file, bytes);
        String checksum = sha256(bytes);
        docs.findByChecksum(checksum).ifPresent(existing -> {
            throw ApiException.conflict("This file was already uploaded as \"" + existing.getTitle() + "\".");
        });

        String filename = cleanFilename(file.getOriginalFilename());
        String key = store(bytes);
        CollegeDocument d = new CollegeDocument();
        d.setTitle(StringUtils.hasText(title) ? title.trim() : stripExtension(filename));
        d.setOriginalFilename(filename);
        d.setCategory(category);
        d.setAudience(audience);
        d.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        d.setStorageKey(key);
        d.setChecksum(checksum);
        d.setFileSize(bytes.length);
        d.setStatus(DocStatus.UPLOADED);
        d.setUploadedBy(userId);
        try {
            d = docs.save(d);
        } catch (DataIntegrityViolationException e) { // lost a race on the unique checksum
            storage.delete(key);
            throw ApiException.conflict("This file was already uploaded.");
        }
        logger.ok(d.getId(), "UPLOADED", "File stored (" + bytes.length + " bytes)");
        ingestion.process(d.getId());
        return DocumentDto.from(d);
    }

    public DocumentDto replace(UUID id, MultipartFile file) {
        CollegeDocument d = get(id);
        requireNotProcessing(d);
        byte[] bytes = read(file);
        int pages = validator.validate(file, bytes);
        String checksum = sha256(bytes);
        docs.findByChecksum(checksum).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw ApiException.conflict("This file was already uploaded as \"" + existing.getTitle() + "\".");
            }
        });

        String oldKey = d.getStorageKey();
        d.setStorageKey(store(bytes));
        d.setOriginalFilename(cleanFilename(file.getOriginalFilename()));
        d.setChecksum(checksum);
        d.setFileSize(bytes.length);
        d.setPageCount(pages);
        d.setChunkCount(0);
        d.setVersion(d.getVersion() + 1);
        d.setStatus(DocStatus.UPLOADED);
        d.setProcessingError(null);
        d = docs.save(d);
        storage.delete(oldKey);
        logger.ok(id, "REPLACED", "File replaced; now version " + d.getVersion());
        ingestion.process(id);
        return DocumentDto.from(d);
    }

    public DocumentDto reindex(UUID id) {
        CollegeDocument d = get(id);
        requireNotProcessing(d);
        d.setStatus(DocStatus.UPLOADED);
        d.setProcessingError(null);
        d = docs.save(d);
        logger.info(id, "REINDEX", "Reindex requested");
        ingestion.process(id);
        return DocumentDto.from(d);
    }

    public void delete(UUID id) {
        CollegeDocument d = get(id);
        requireNotProcessing(d);
        vectors.deleteByDocument(id);
        docs.delete(d);
        storage.delete(d.getStorageKey());
    }

    /** Edits metadata and immediately syncs the vector metadata so audience changes take effect at once. */
    public DocumentDto updateMetadata(UUID id, UpdateDocumentRequest req) {
        validateCategory(req.category());
        CollegeDocument d = get(id);
        requireNotProcessing(d);
        d.setTitle(req.title().trim());
        d.setCategory(req.category());
        d.setAudience(req.audience());
        d.setDescription(StringUtils.hasText(req.description()) ? req.description().trim() : null);
        d = docs.save(d);
        vectors.updateMetadata(id, d.getTitle(), d.getCategory(), d.getAudience());
        return DocumentDto.from(d);
    }

    public List<DocumentDto> list(String query, DocStatus status) {
        String p = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        List<CollegeDocument> found = status == null ? docs.search(p) : docs.searchByStatus(p, status);
        return found.stream().map(DocumentDto::from).toList();
    }

    public DocumentDto details(UUID id) {
        return DocumentDto.from(get(id));
    }

    public StatusDto status(UUID id) {
        CollegeDocument d = get(id);
        List<LogDto> trail = logs.findByDocumentIdOrderByCreatedAtAscIdAsc(id).stream().map(LogDto::from).toList();
        return new StatusDto(d.getId(), d.getStatus(), d.getProcessingError(), d.getPageCount(), d.getChunkCount(),
                trail);
    }

    // ---------- user-facing reads (permission checked) ----------

    public List<DocumentSummary> visibleTo(Role role) {
        return docs.findByStatusAndAudienceInOrderByTitleAsc(DocStatus.READY, AccessPolicy.allowedAudiences(role))
                .stream().map(DocumentSummary::from).toList();
    }

    /**
     * Returns the document only if this role may see it. Anything else is reported as "not found" so the
     * existence of restricted documents is not revealed. Non-admins only see READY documents.
     */
    public CollegeDocument getAccessible(UUID id, Role role) {
        CollegeDocument d = docs.findById(id).orElseThrow(() -> ApiException.notFound("Document not found."));
        boolean allowed = AccessPolicy.canView(role, d.getAudience())
                && (role == Role.ADMIN || d.getStatus() == DocStatus.READY);
        if (!allowed) {
            throw ApiException.notFound("Document not found.");
        }
        return d;
    }

    public InputStream openFile(CollegeDocument d) {
        try {
            return storage.open(d.getStorageKey());
        } catch (IOException e) {
            throw ApiException.notFound("The stored file is missing.");
        }
    }

    // ---------- helpers ----------

    private CollegeDocument get(UUID id) {
        return docs.findById(id).orElseThrow(() -> ApiException.notFound("Document not found."));
    }

    private void requireNotProcessing(CollegeDocument d) {
        if (d.getStatus() == DocStatus.PROCESSING) {
            throw ApiException.conflict("This document is being processed. Try again when it finishes.");
        }
    }

    private void validateCategory(String category) {
        if (category == null || !props.getCategories().contains(category)) {
            throw ApiException.badRequest("Unknown category. Allowed: " + String.join(", ", props.getCategories()));
        }
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read.");
        }
    }

    private String store(byte[] bytes) {
        try {
            return storage.store(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store file", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cleanFilename(String name) {
        String n = StringUtils.getFilename(StringUtils.cleanPath(name == null ? "document.pdf" : name));
        if (n == null || n.isBlank()) {
            n = "document.pdf";
        }
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
