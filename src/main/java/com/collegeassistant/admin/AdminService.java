package com.collegeassistant.admin;

import com.collegeassistant.chat.MessageRepository;
import com.collegeassistant.chat.MessageRole;
import com.collegeassistant.chat.UnansweredQuestion;
import com.collegeassistant.chat.UnansweredQuestionRepository;
import com.collegeassistant.document.DocStatus;
import com.collegeassistant.document.DocumentDtos.DocumentDto;
import com.collegeassistant.document.DocumentRepository;
import com.collegeassistant.feedback.FeedbackRepository;
import com.collegeassistant.feedback.FeedbackRow;
import com.collegeassistant.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard numbers come straight from the database; nothing is estimated or made up. */
@Service
@Transactional(readOnly = true)
public class AdminService {

    public record Stats(long documents, long readyDocuments, long failedDocuments, long users, long questions,
                        long unansweredQuestions, long helpfulFeedback, long unhelpfulFeedback,
                        List<DocumentDto> recentUploads) {
    }

    public record UnansweredDto(Long id, String question, Instant createdAt) {
    }

    private final DocumentRepository docs;
    private final UserRepository users;
    private final MessageRepository messages;
    private final UnansweredQuestionRepository unanswered;
    private final FeedbackRepository feedback;

    public AdminService(DocumentRepository docs, UserRepository users, MessageRepository messages,
                        UnansweredQuestionRepository unanswered, FeedbackRepository feedback) {
        this.docs = docs;
        this.users = users;
        this.messages = messages;
        this.unanswered = unanswered;
        this.feedback = feedback;
    }

    public Stats stats() {
        return new Stats(
                docs.count(),
                docs.countByStatus(DocStatus.READY),
                docs.countByStatus(DocStatus.FAILED),
                users.count(),
                messages.countByRole(MessageRole.USER),
                messages.countByRoleAndAnswerable(MessageRole.ASSISTANT, false),
                feedback.countByRating((short) 1),
                feedback.countByRating((short) -1),
                docs.findTop5ByOrderByCreatedAtDesc().stream().map(DocumentDto::from).toList());
    }

    public List<UnansweredDto> unanswered(int page, int size) {
        return unanswered.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), clamp(size)))
                .stream().map(this::toDto).toList();
    }

    public List<FeedbackRow> feedback(int page, int size) {
        return feedback.findAdminRows(PageRequest.of(Math.max(0, page), clamp(size)));
    }

    private UnansweredDto toDto(UnansweredQuestion u) {
        return new UnansweredDto(u.getId(), u.getQuestion(), u.getCreatedAt());
    }

    private int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
