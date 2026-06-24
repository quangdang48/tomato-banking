package com.tomato.modules.onboarding.repository;

import com.tomato.modules.onboarding.entity.KycVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {

    Optional<KycVerification> findByProfileId(Long profileId);
}
