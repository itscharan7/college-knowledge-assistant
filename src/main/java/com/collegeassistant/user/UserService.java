package com.collegeassistant.user;

import com.collegeassistant.common.ApiException;
import com.collegeassistant.user.UserDtos.CreateUserRequest;
import com.collegeassistant.user.UserDtos.RegisterForm;
import com.collegeassistant.user.UserDtos.UpdateUserRequest;
import com.collegeassistant.user.UserDtos.UserDto;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Public self-registration. The role is ALWAYS STUDENT; nothing in the input can change that. */
    @Transactional
    public User registerStudent(RegisterForm form) {
        if (!form.password().equals(form.confirmPassword())) {
            throw ApiException.badRequest("Passwords do not match.");
        }
        return create(form.name(), form.email(), form.password(), Role.STUDENT);
    }

    /** Admin-created accounts (typically FACULTY). Admin accounts are not created through this path. */
    @Transactional
    public UserDto createByAdmin(CreateUserRequest req) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.badRequest("Create the account as STUDENT or FACULTY, then promote it if needed.");
        }
        return UserDto.from(create(req.name(), req.email(), req.password(), req.role()));
    }

    @Transactional
    public User createBootstrapAdmin(String name, String email, String password) {
        return create(name, email, password, Role.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UserDto> list(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return users.search("%" + q + "%").stream().map(UserDto::from).toList();
    }

    /** Admins cannot change their own role or active flag (prevents locking the system out). */
    @Transactional
    public UserDto update(Long targetId, UpdateUserRequest req, Long actingUserId) {
        if (targetId.equals(actingUserId)) {
            throw ApiException.badRequest("You cannot change your own role or status.");
        }
        User u = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (req.role() != null) {
            u.setRole(req.role());
        }
        if (req.active() != null) {
            u.setActive(req.active());
        }
        return UserDto.from(users.save(u));
    }

    private User create(String name, String email, String rawPassword, Role role) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        User u = new User();
        u.setName(name.trim());
        u.setEmail(normalized);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(role);
        u.setActive(true);
        return users.save(u);
    }
}
