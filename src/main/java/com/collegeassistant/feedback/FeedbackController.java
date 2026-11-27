package com.collegeassistant.feedback;

import com.collegeassistant.security.AppUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    public record FeedbackRequest(@NotNull Long messageId, @NotNull Integer rating, @Size(max = 1000) String comment) {
    }

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody FeedbackRequest req,
                                       @AuthenticationPrincipal AppUserDetails me) {
        service.submit(me.getId(), req.messageId(), req.rating(), req.comment());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
