package com.tomato.modules.onboarding.repository;

import com.tomato.modules.onboarding.entity.CustomerProfile;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    Optional<CustomerProfile> findByUserId(Integer userId);

    boolean existsByUserId(Integer userId);

    Page<CustomerProfile> findByStatus(OnboardingStatus status, Pageable pageable);
}
