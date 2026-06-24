package com.tomato.modules.onboarding.support;

import com.tomato.config.OnboardingProperties;
import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import com.tomato.modules.auth.security.CurrentUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Temporary reviewer/admin gate for onboarding review endpoints. Replace with a real role
 * model in a later phase. An empty reviewer list permits any authenticated user (dev only).
 */
@Component
@RequiredArgsConstructor
public class OnboardingAdminGuard {

    private final OnboardingProperties properties;

    public void assertReviewer() {
        String username = CurrentUserPrincipal.require().username();
        var reviewers = properties.reviewerUsernames();
        if (reviewers == null || reviewers.isEmpty()) {
            return; // dev/admin testing: no allowlist configured
        }
        if (!reviewers.contains(username)) {
            throw new BusinessException(ErrorCode.ERROR_403_2309);
        }
    }
}
