package com.tomato.modules.onboarding.controller;

import com.tomato.common.ApiResponse;
import com.tomato.modules.auth.security.CurrentUserPrincipal;
import com.tomato.modules.onboarding.dto.request.ReviewOnboardingRequest;
import com.tomato.modules.onboarding.dto.response.OnboardingStatusResponse;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import com.tomato.modules.onboarding.service.OnboardingReviewService;
import com.tomato.modules.onboarding.support.OnboardingAdminGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/onboarding")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Onboarding Review", description = "Reviewer/admin onboarding APIs (temporary admin gate)")
public class OnboardingReviewController {

    private final OnboardingReviewService reviewService;
    private final OnboardingAdminGuard adminGuard;

    @GetMapping("/reviews")
    @Operation(summary = "List the onboarding review queue")
    public ResponseEntity<ApiResponse<Page<OnboardingStatusResponse>>> listReviews(
            @RequestParam(required = false) OnboardingStatus status,
            Pageable pageable) {
        adminGuard.assertReviewer();
        Page<OnboardingStatusResponse> body =
                reviewService.listReviews(status, pageable).map(OnboardingStatusResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping("/reviews/{profileId}")
    @Operation(summary = "Get review details for a profile")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getReview(@PathVariable Long profileId) {
        adminGuard.assertReviewer();
        return ResponseEntity.ok(ApiResponse.success(
                OnboardingStatusResponse.from(reviewService.getReview(profileId))));
    }

    @PostMapping("/reviews/{profileId}/start")
    @Operation(summary = "Move a submitted profile to IN_REVIEW")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> startReview(
            @AuthenticationPrincipal CurrentUserPrincipal reviewer,
            @PathVariable Long profileId) {
        adminGuard.assertReviewer();
        return ResponseEntity.ok(ApiResponse.success(
                OnboardingStatusResponse.from(reviewService.startReview(reviewer.userId(), profileId))));
    }

    @PostMapping("/reviews/{profileId}/decision")
    @Operation(summary = "Approve, reject, or request more info on a profile")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> decide(
            @AuthenticationPrincipal CurrentUserPrincipal reviewer,
            @PathVariable Long profileId,
            @Valid @RequestBody ReviewOnboardingRequest request) {
        adminGuard.assertReviewer();
        return ResponseEntity.ok(ApiResponse.success(
                OnboardingStatusResponse.from(reviewService.decide(reviewer.userId(), profileId, request))));
    }
}
