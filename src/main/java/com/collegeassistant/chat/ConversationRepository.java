package com.collegeassistant.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Conversation> findByIdAndUserId(UUID id, Long userId);

    boolean existsByIdAndUserId(UUID id, Long userId);
}
