package com.collegeassistant.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where original PDFs live. The rest of the app only sees opaque keys, so a hosted deployment can
 * swap the local-disk implementation for an object-storage one without touching business code.
 */
public interface FileStorageService {

    /** Stores the bytes and returns a generated key (never derived from the user's file name). */
    String store(byte[] content) throws IOException;

    InputStream open(String key) throws IOException;

    /** Deletes quietly; a missing file is not an error. */
    void delete(String key);
}
