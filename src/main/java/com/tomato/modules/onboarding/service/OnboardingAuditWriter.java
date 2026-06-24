package com.tomato.modules.onboarding.service;

import com.tomato.modules.onboarding.entity.OnboardingAuditLog;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import com.tomato.modules.onboarding.repository.OnboardingAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Writes onboarding audit rows. Called inside the same transaction as the status
 * transition it records, so an audit gap cannot occur if the transition commits.
 */
@Component
@RequiredArgsConstructor
public class OnboardingAuditWriter {

    private final OnboardingAuditLogRepository auditLogRepository;

    public void record(Long profileId,
                       Integer actorUserId,
                       String action,
                       OnboardingStatus oldStatus,
                       OnboardingStatus newStatus,
                       String reason) {
        auditLogRepository.save(OnboardingAuditLog.builder()
                .profileId(profileId)
                .actorUserId(actorUserId)
                .action(action)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason(reason)
                .build());
    }
}
