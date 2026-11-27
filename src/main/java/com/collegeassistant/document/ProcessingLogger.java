package com.collegeassistant.document;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes the per-document processing trail that admins see when something fails. */
@Component
public class ProcessingLogger {

    private final ProcessingLogRepository repo;

    public ProcessingLogger(ProcessingLogRepository repo) {
        this.repo = repo;
    }

    public void info(UUID documentId, String stage, String message) {
        write(documentId, stage, "INFO", message);
    }

    public void ok(UUID documentId, String stage, String message) {
        write(documentId, stage, "OK", message);
    }

    public void error(UUID documentId, String stage, String message) {
        write(documentId, stage, "ERROR", message);
    }

    private void write(UUID documentId, String stage, String status, String message) {
        ProcessingLog l = new ProcessingLog();
        l.setDocumentId(documentId);
        l.setStage(stage);
        l.setStatus(status);
        l.setMessage(message != null && message.length() > 2000 ? message.substring(0, 2000) : message);
        repo.save(l);
    }
}
