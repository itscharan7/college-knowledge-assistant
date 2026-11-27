package com.collegeassistant.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingLogRepository extends JpaRepository<ProcessingLog, Long> {

    List<ProcessingLog> findByDocumentIdOrderByCreatedAtAscIdAsc(UUID documentId);
}
