package com.tomato.modules.onboarding.service;

import com.tomato.modules.onboarding.dto.request.AddVerificationDocumentRequest;
import com.tomato.modules.onboarding.dto.request.CreateOnboardingProfileRequest;
import com.tomato.modules.onboarding.dto.request.UpsertKycRequest;
import com.tomato.modules.onboarding.entity.CustomerProfile;
import com.tomato.modules.onboarding.entity.KycVerification;
import com.tomato.modules.onboarding.entity.VerificationDocument;

/**
 * Customer-facing onboarding workflow (individual eKYC slice). Approval is decided here,
 * never in controllers.
 */
public interface OnboardingService {

    CustomerProfile createProfile(Integer userId, CreateOnboardingProfileRequest request);

    CustomerProfile getProfile(Integer userId);

    KycVerification upsertKyc(Integer userId, UpsertKycRequest request);

    VerificationDocument addDocument(Integer userId, AddVerificationDocumentRequest request);

    CustomerProfile submit(Integer userId);

    /**
     * Banking gate: throws {@code ERROR_403_2302} unless the user's onboarding is APPROVED.
     */
    void requireApproved(Integer userId);
}
