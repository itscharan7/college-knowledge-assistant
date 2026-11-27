package com.collegeassistant.feedback;

import com.collegeassistant.chat.ConversationRepository;
import com.collegeassistant.chat.Message;
import com.collegeassistant.chat.MessageRepository;
import com.collegeassistant.chat.MessageRole;
import com.collegeassistant.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedback;
    private final MessageRepository messages;
    private final ConversationRepository conversations;

    public FeedbackService(FeedbackRepository feedback, MessageRepository messages,
                           ConversationRepository conversations) {
        this.feedback = feedback;
        this.messages = messages;
        this.conversations = conversations;
    }

    /**
     * One rating per (message, user); submitting again updates it. Users can only rate assistant
     * messages inside their own conversations (otherwise 404, nothing is revealed).
     */
    @Transactional
    public void submit(Long userId, Long messageId, int rating, String comment) {
        if (rating != 1 && rating != -1) {
            throw ApiException.badRequest("Rating must be 1 (helpful) or -1 (not helpful).");
        }
        Message m = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message not found."));
        if (m.getRole() != MessageRole.ASSISTANT || !conversations.existsByIdAndUserId(m.getConversationId(), userId)) {
            throw ApiException.notFound("Message not found.");
        }
        Feedback f = feedback.findByMessageIdAndUserId(messageId, userId).orElseGet(Feedback::new);
        f.setMessageId(messageId);
        f.setUserId(userId);
        f.setRating((short) rating);
        String c = comment == null ? null : comment.strip();
        f.setComment(c == null || c.isEmpty() ? null : (c.length() > 1000 ? c.substring(0, 1000) : c));
        feedback.save(f);
    }
}
