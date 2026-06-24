package com.tomato.modules.onboarding.dto.request;

import com.tomato.modules.onboarding.enums.OnboardingStatus;
import com.tomato.modules.onboarding.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Reviewer decision on an onboarding profile")
public record ReviewOnboardingRequest(
        @Schema(description = "Decision outcome", example = "APPROVED",
                allowableValues = {"APPROVED", "REJECTED", "REQUIRES_MORE_INFO"})
        @NotNull(message = "Decision is required")
        OnboardingStatus decision,

        @Schema(description = "Internal risk level assigned by the reviewer", example = "LOW")
        RiskLevel riskLevel,

        @Schema(description = "Reason for the decision. Required when rejecting or requesting more info.",
                example = "Verified manually")
        String reason
) {
}
