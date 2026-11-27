package com.collegeassistant.chat;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageSourceRepository extends JpaRepository<MessageSource, Long> {

    List<MessageSource> findByMessageIdIn(Collection<Long> messageIds);
}
