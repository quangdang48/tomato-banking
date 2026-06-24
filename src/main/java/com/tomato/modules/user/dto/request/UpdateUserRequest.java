package com.tomato.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

@Schema(description = "Request body for updating a user profile")
public record UpdateUserRequest(
        @Schema(description = "User display name", example = "Tom A")
        String fullName,

        @Schema(description = "Email address", example = "tomato@example.com")
        @Email(message = "Email must be valid")
        String email
) {
}
