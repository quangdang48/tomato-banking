package com.tomato.modules.onboarding.dto.request;

import com.tomato.modules.onboarding.enums.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to create the caller's onboarding profile")
public record CreateOnboardingProfileRequest(
        @Schema(description = "Type of customer being onboarded", example = "INDIVIDUAL")
        @NotNull(message = "Customer type is required")
        CustomerType customerType
) {
}
