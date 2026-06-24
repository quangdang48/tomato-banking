package com.tomato.modules.onboarding.repository;

import com.tomato.modules.onboarding.entity.OnboardingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnboardingAuditLogRepository extends JpaRepository<OnboardingAuditLog, Long> {

    List<OnboardingAuditLog> findByProfileIdOrderByCreatedAtAsc(Long profileId);
}
