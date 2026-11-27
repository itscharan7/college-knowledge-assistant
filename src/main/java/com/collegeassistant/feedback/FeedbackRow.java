package com.collegeassistant.feedback;

import java.time.Instant;

/** Admin-facing feedback line: who rated, how, and the answer text that was rated. */
public record FeedbackRow(Long id, Short rating, String comment, Instant createdAt, String userEmail,
                          String answer) {
}
