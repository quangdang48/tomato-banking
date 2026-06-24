package com.tomato.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for username/password login")
public record LoginRequest(
        @Schema(description = "Username registered for the account", example = "tomato")
        @NotBlank(message = "Username is required")
        String username,

        @Schema(
                description = "Raw password for credential verification",
                example = "secret123",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank(message = "Password is required")
        String password
) {
}
