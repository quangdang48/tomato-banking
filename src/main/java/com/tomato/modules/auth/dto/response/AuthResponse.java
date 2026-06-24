package com.tomato.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT access token response returned after successful login")
public record AuthResponse(
        @Schema(description = "Signed JWT access token", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0b21hdG8ifQ.signature")
        String token,

        @Schema(description = "Token lifetime in seconds", example = "3600")
        long expiresIn
) {

    public static AuthResponse of(String token, long expiresIn) {
        return new AuthResponse(token, expiresIn);
    }
}
