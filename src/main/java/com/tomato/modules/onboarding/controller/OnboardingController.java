package com.tomato.modules.onboarding.controller;

import com.tomato.common.ApiResponse;
import com.tomato.modules.auth.security.CurrentUserPrincipal;
import com.tomato.modules.onboarding.dto.request.AddVerificationDocumentRequest;
import com.tomato.modules.onboarding.dto.request.CreateOnboardingProfileRequest;
import com.tomato.modules.onboarding.dto.request.UpsertKycRequest;
import com.tomato.modules.onboarding.dto.response.OnboardingStatusResponse;
import com.tomato.modules.onboarding.entity.VerificationDocument;
import com.tomato.modules.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Onboarding", description = "Customer onboarding (individual eKYC) APIs")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/profile")
    @Operation(summary = "Create the caller's onboarding profile")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> createProfile(
            @AuthenticationPrincipal CurrentUserPrincipal user,
            @Valid @RequestBody CreateOnboardingProfileRequest request) {
        OnboardingStatusResponse body = OnboardingStatusResponse.from(
                onboardingService.createProfile(user.userId(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    @GetMapping("/status")
    @Operation(summary = "Get the caller's onboarding status")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getStatus(@AuthenticationPrincipal CurrentUserPrincipal user) {
        OnboardingStatusResponse body = OnboardingStatusResponse.from(
                onboardingService.getProfile(user.userId()));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/kyc")
    @Operation(summary = "Create or update individual KYC details")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> upsertKyc(
            @AuthenticationPrincipal CurrentUserPrincipal user,
            @Valid @RequestBody UpsertKycRequest request) {
        onboardingService.upsertKyc(user.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(
                OnboardingStatusResponse.from(onboardingService.getProfile(user.userId()))));
    }

    @PostMapping("/documents")
    @Operation(summary = "Add verification document metadata")
    public ResponseEntity<ApiResponse<Long>> addDocument(
            @AuthenticationPrincipal CurrentUserPrincipal user,
            @Valid @RequestBody AddVerificationDocumentRequest request) {
        VerificationDocument document = onboardingService.addDocument(user.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(document.getId()));
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit onboarding for review")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> submit(@AuthenticationPrincipal CurrentUserPrincipal user) {
        OnboardingStatusResponse body = OnboardingStatusResponse.from(
                onboardingService.submit(user.userId()));
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
