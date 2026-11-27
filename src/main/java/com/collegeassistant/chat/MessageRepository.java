package com.collegeassistant.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);

    long countByRole(MessageRole role);

    long countByRoleAndAnswerable(MessageRole role, Boolean answerable);

    @Query("select count(m) from Message m where m.role = com.collegeassistant.chat.MessageRole.USER "
            + "and m.createdAt >= :since and m.conversationId in "
            + "(select c.id from Conversation c where c.userId = :userId)")
    long countUserQuestionsSince(@Param("userId") Long userId, @Param("since") Instant since);
}
