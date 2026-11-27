package com.collegeassistant.chat;

import com.collegeassistant.chat.ChatDtos.AnswerResponse;
import com.collegeassistant.chat.ChatDtos.AskRequest;
import com.collegeassistant.chat.ChatDtos.ConversationDto;
import com.collegeassistant.chat.ChatDtos.MessageDto;
import com.collegeassistant.rag.RagService;
import com.collegeassistant.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller: identity comes from the session, all rules live in the services. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService conversations;
    private final RagService rag;

    public ChatController(ConversationService conversations, RagService rag) {
        this.conversations = conversations;
        this.rag = rag;
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> create(@AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversations.create(me.getId()));
    }

    @GetMapping("/conversations")
    public List<ConversationDto> list(@AuthenticationPrincipal AppUserDetails me) {
        return conversations.list(me.getId());
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDto> messages(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me) {
        return conversations.messages(me.getId(), id);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me) {
        conversations.delete(me.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/ask")
    public AnswerResponse ask(@PathVariable UUID id, @Valid @RequestBody AskRequest req,
                              @AuthenticationPrincipal AppUserDetails me) {
        return rag.ask(me.getId(), me.getRole(), id, req.question());
    }
}
