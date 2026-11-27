package com.collegeassistant.user;

import com.collegeassistant.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class ProfileController {

    public record Me(Long id, String name, String email, Role role) {
    }

    @GetMapping
    public Me me(@AuthenticationPrincipal AppUserDetails me) {
        return new Me(me.getId(), me.getName(), me.getUsername(), me.getRole());
    }
}
