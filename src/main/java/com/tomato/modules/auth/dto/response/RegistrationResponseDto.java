package com.tomato.modules.auth.dto.response;

import com.tomato.modules.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response returned after successful user registration")
public record RegistrationResponseDto(
        @Schema(description = "User ID", example = "1")
        Integer id,

        @Schema(description = "Username", example = "tomato")
        String username,

        @Schema(description = "Email address", example = "tomato@example.com")
        String email,

        @Schema(description = "User display name", example = "Tom A")
        String fullName
) {

    public static RegistrationResponseDto from(User user) {
        return new RegistrationResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName()
        );
    }
}
