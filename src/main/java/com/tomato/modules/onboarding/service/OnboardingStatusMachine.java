package com.tomato.modules.onboarding.service;

import com.tomato.modules.onboarding.enums.OnboardingStatus;
import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;

import java.util.Map;
import java.util.Set;

import static com.tomato.modules.onboarding.enums.OnboardingStatus.APPROVED;
import static com.tomato.modules.onboarding.enums.OnboardingStatus.DRAFT;
import static com.tomato.modules.onboarding.enums.OnboardingStatus.IN_REVIEW;
import static com.tomato.modules.onboarding.enums.OnboardingStatus.REJECTED;
import static com.tomato.modules.onboarding.enums.OnboardingStatus.REQUIRES_MORE_INFO;
import static com.tomato.modules.onboarding.enums.OnboardingStatus.SUBMITTED;

/**
 * Central guard for onboarding status transitions. Any transition not in the allowed
 * set is rejected with {@code ERROR_409_2306}.
 */
public final class OnboardingStatusMachine {

    private static final Map<OnboardingStatus, Set<OnboardingStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(SUBMITTED),
            SUBMITTED, Set.of(IN_REVIEW),
            IN_REVIEW, Set.of(APPROVED, REJECTED, REQUIRES_MORE_INFO),
            REQUIRES_MORE_INFO, Set.of(SUBMITTED),
            REJECTED, Set.of(SUBMITTED)
    );

    private OnboardingStatusMachine() {
    }

    public static boolean isAllowed(OnboardingStatus from, OnboardingStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(OnboardingStatus from, OnboardingStatus to) {
        if (!isAllowed(from, to)) {
            throw new BusinessException(
                    ErrorCode.ERROR_409_2306,
                    "Illegal onboarding transition: " + from + " -> " + to
            );
        }
    }
}
