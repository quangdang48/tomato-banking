package com.tomato.modules.onboarding.service;

import com.tomato.modules.onboarding.enums.OnboardingStatus;

/**
 * Audit action labels written to {@code onboarding_audit_logs.action}. Centralized to
 * avoid magic strings scattered across the onboarding services.
 */
public final class OnboardingAuditAction {

    public static final String CREATE_PROFILE = "CREATE_PROFILE";
    public static final String SUBMIT = "SUBMIT";
    public static final String START_REVIEW = "START_REVIEW";
    public static final String DECISION_PREFIX = "DECISION_";

    private OnboardingAuditAction() {
    }

    public static String decision(OnboardingStatus decision) {
        return DECISION_PREFIX + decision;
    }
}
