package com.collegeassistant.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnansweredQuestionRepository extends JpaRepository<UnansweredQuestion, Long> {

    List<UnansweredQuestion> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
