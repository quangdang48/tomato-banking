package com.tomato.modules.onboarding.service;

import com.tomato.modules.onboarding.dto.request.ReviewOnboardingRequest;
import com.tomato.modules.onboarding.entity.CustomerProfile;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Reviewer/admin onboarding workflow. Every decision writes an audit row in the same
 * transaction as the status transition.
 */
public interface OnboardingReviewService {

    Page<CustomerProfile> listReviews(OnboardingStatus status, Pageable pageable);

    CustomerProfile getReview(Long profileId);

    CustomerProfile startReview(Integer reviewerUserId, Long profileId);

    CustomerProfile decide(Integer reviewerUserId, Long profileId, ReviewOnboardingRequest request);
}
