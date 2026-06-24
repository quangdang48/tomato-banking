package com.tomato.modules.onboarding.service;

import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import com.tomato.modules.onboarding.dto.request.ReviewOnboardingRequest;
import com.tomato.modules.onboarding.entity.CustomerProfile;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import com.tomato.modules.onboarding.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnboardingReviewServiceImpl implements OnboardingReviewService {

    private static final Set<OnboardingStatus> VALID_DECISIONS = Set.of(
            OnboardingStatus.APPROVED,
            OnboardingStatus.REJECTED,
            OnboardingStatus.REQUIRES_MORE_INFO
    );
    private static final Set<OnboardingStatus> REASON_REQUIRED = Set.of(
            OnboardingStatus.REJECTED,
            OnboardingStatus.REQUIRES_MORE_INFO
    );

    private final CustomerProfileRepository profileRepository;
    private final OnboardingAuditWriter auditWriter;

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerProfile> listReviews(OnboardingStatus status, Pageable pageable) {
        if (status == null) {
            return profileRepository.findAll(pageable);
        }
        return profileRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfile getReview(Long profileId) {
        return requireProfile(profileId);
    }

    @Override
    @Transactional
    public CustomerProfile startReview(Integer reviewerUserId, Long profileId) {
        CustomerProfile profile = requireProfile(profileId);
        OnboardingStatus from = profile.getStatus();
        OnboardingStatusMachine.assertTransition(from, OnboardingStatus.IN_REVIEW);
        profile.setStatus(OnboardingStatus.IN_REVIEW);
        CustomerProfile saved = profileRepository.save(profile);
        auditWriter.record(saved.getId(), reviewerUserId, OnboardingAuditAction.START_REVIEW, from, OnboardingStatus.IN_REVIEW, null);
        return saved;
    }

    @Override
    @Transactional
    public CustomerProfile decide(Integer reviewerUserId, Long profileId, ReviewOnboardingRequest request) {
        OnboardingStatus decision = request.decision();
        if (!VALID_DECISIONS.contains(decision)) {
            throw new BusinessException(
                    ErrorCode.ERROR_400_VALIDATION,
                    "Decision must be APPROVED, REJECTED, or REQUIRES_MORE_INFO"
            );
        }
        if (REASON_REQUIRED.contains(decision) && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(
                    ErrorCode.ERROR_400_VALIDATION,
                    "Reason is required when rejecting or requesting more info"
            );
        }

        CustomerProfile profile = requireProfile(profileId);
        OnboardingStatus from = profile.getStatus();
        OnboardingStatusMachine.assertTransition(from, decision);

        profile.setStatus(decision);
        if (request.riskLevel() != null) {
            profile.setRiskLevel(request.riskLevel());
        }
        profile.setReviewedAt(Instant.now());
        CustomerProfile saved = profileRepository.save(profile);
        auditWriter.record(saved.getId(), reviewerUserId, OnboardingAuditAction.decision(decision), from, decision, request.reason());
        return saved;
    }

    private CustomerProfile requireProfile(Long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERROR_404_2300));
    }
}
