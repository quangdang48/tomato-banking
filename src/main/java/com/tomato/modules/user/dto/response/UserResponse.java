package com.tomato.modules.user.dto.response;

import com.tomato.modules.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public user profile response")
public record UserResponse(
        @Schema(description = "User ID", example = "1")
        Integer id,

        @Schema(description = "Username", example = "tomato")
        String username,

        @Schema(description = "Email address", example = "tomato@example.com")
        String email,

        @Schema(description = "User display name", example = "Tom A")
        String fullName
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName()
        );
    }
}
