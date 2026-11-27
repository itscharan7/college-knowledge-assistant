package com.collegeassistant.feedback;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByMessageIdAndUserId(Long messageId, Long userId);

    long countByRating(Short rating);

    @Query("select new com.collegeassistant.feedback.FeedbackRow(f.id, f.rating, f.comment, f.createdAt, "
            + "u.email, m.content) from Feedback f, com.collegeassistant.user.User u, "
            + "com.collegeassistant.chat.Message m where u.id = f.userId and m.id = f.messageId "
            + "order by f.createdAt desc")
    List<FeedbackRow> findAdminRows(Pageable pageable);
}
