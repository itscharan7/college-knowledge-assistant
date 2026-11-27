package com.collegeassistant.ingestion;

/** A processing failure whose message is safe and useful to show to an admin. */
public class IngestionException extends RuntimeException {
    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
