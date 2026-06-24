package com.tomato.modules.onboarding.dto.response;

import com.tomato.modules.onboarding.entity.CustomerProfile;
import com.tomato.modules.onboarding.enums.CustomerType;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "High-level onboarding status. Excludes identity payloads and risk rationale.")
public record OnboardingStatusResponse(
        @Schema(description = "Onboarding profile ID", example = "10")
        Long profileId,

        @Schema(description = "Customer type", example = "INDIVIDUAL")
        CustomerType customerType,

        @Schema(description = "Current onboarding status", example = "DRAFT")
        OnboardingStatus status,

        @Schema(description = "When the profile was submitted for review")
        Instant submittedAt,

        @Schema(description = "When the review decision was recorded")
        Instant reviewedAt
) {

    public static OnboardingStatusResponse from(CustomerProfile profile) {
        return new OnboardingStatusResponse(
                profile.getId(),
                profile.getCustomerType(),
                profile.getStatus(),
                profile.getSubmittedAt(),
                profile.getReviewedAt()
        );
    }
}
