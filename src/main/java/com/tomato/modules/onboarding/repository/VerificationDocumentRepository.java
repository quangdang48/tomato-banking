package com.tomato.modules.onboarding.repository;

import com.tomato.modules.onboarding.entity.VerificationDocument;
import com.tomato.modules.onboarding.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, Long> {

    List<VerificationDocument> findByProfileId(Long profileId);

    boolean existsByProfileIdAndDocumentTypeAndStatus(
            Long profileId,
            String documentType,
            DocumentStatus status
    );
}
