package com.tomato.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating a user")
public record CreateUserRequest(
        @Schema(description = "Unique username", example = "tomato")
        @NotBlank(message = "Username is required")
        String username,

        @Schema(description = "Unique email address", example = "tomato@example.com")
        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        String email,

        @Schema(description = "User display name", example = "Tom A")
        String fullName,

        @Schema(
                description = "Raw password. Auth flows should store this as a BCrypt hash.",
                example = "secret123",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password
) {
}
