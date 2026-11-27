package com.collegeassistant.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** DTOs for the user module (kept together: they are tiny). */
public final class UserDtos {

    private UserDtos() {
    }

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "must be 8-72 characters and contain a letter and a digit";

    public record UserDto(Long id, String name, String email, Role role, boolean active, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive(), u.getCreatedAt());
        }
    }

    /** Public registration form. Note: there is deliberately NO role field. */
    public record RegisterForm(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotBlank String confirmPassword) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotNull Role role) {
    }

    public record UpdateUserRequest(Role role, Boolean active) {
    }
}
