package com.collegeassistant.storage;

import com.collegeassistant.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(AppProperties props) throws IOException {
        this.root = Path.of(props.getStorage().getDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(byte[] content) throws IOException {
        String key = UUID.randomUUID() + ".pdf";
        Files.write(resolve(key), content);
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    /** Resolves a key and refuses anything that would escape the storage directory. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }
}
