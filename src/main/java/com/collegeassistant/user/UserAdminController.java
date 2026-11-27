package com.collegeassistant.user;

import com.collegeassistant.security.AppUserDetails;
import com.collegeassistant.user.UserDtos.CreateUserRequest;
import com.collegeassistant.user.UserDtos.UpdateUserRequest;
import com.collegeassistant.user.UserDtos.UserDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService service;

    public UserAdminController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserDto> list(@RequestParam(required = false) String q) {
        return service.list(q);
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createByAdmin(req));
    }

    @PatchMapping("/{id}")
    public UserDto update(@PathVariable Long id, @RequestBody UpdateUserRequest req,
                          @AuthenticationPrincipal AppUserDetails me) {
        return service.update(id, req, me.getId());
    }
}
