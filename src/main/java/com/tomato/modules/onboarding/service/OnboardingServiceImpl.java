package com.tomato.modules.onboarding.service;

import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import com.tomato.modules.onboarding.dto.request.AddVerificationDocumentRequest;
import com.tomato.modules.onboarding.dto.request.CreateOnboardingProfileRequest;
import com.tomato.modules.onboarding.dto.request.UpsertKycRequest;
import com.tomato.modules.onboarding.entity.CustomerProfile;
import com.tomato.modules.onboarding.entity.KycVerification;
import com.tomato.modules.onboarding.entity.VerificationDocument;
import com.tomato.modules.onboarding.enums.CustomerType;
import com.tomato.modules.onboarding.enums.DocumentStatus;
import com.tomato.modules.onboarding.enums.OnboardingStatus;
import com.tomato.modules.onboarding.enums.VerificationStatus;
import com.tomato.modules.onboarding.repository.CustomerProfileRepository;
import com.tomato.modules.onboarding.repository.KycVerificationRepository;
import com.tomato.modules.onboarding.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private static final Set<OnboardingStatus> EDITABLE_STATES =
            Set.of(OnboardingStatus.DRAFT, OnboardingStatus.REQUIRES_MORE_INFO);
    private static final String CCCD = "CCCD";

    private final CustomerProfileRepository profileRepository;
    private final KycVerificationRepository kycRepository;
    private final VerificationDocumentRepository documentRepository;
    private final OnboardingAuditWriter auditWriter;

    @Override
    @Transactional
    public CustomerProfile createProfile(Integer userId, CreateOnboardingProfileRequest request) {
        if (profileRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.ERROR_409_2301);
        }
        CustomerProfile profile = profileRepository.save(CustomerProfile.builder()
                .userId(userId)
                .customerType(request.customerType())
                .status(OnboardingStatus.DRAFT)
                .build());
        auditWriter.record(profile.getId(), userId, OnboardingAuditAction.CREATE_PROFILE, null, OnboardingStatus.DRAFT, null);
        return profile;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfile getProfile(Integer userId) {
        return requireProfile(userId);
    }

    @Override
    @Transactional
    public KycVerification upsertKyc(Integer userId, UpsertKycRequest request) {
        CustomerProfile profile = requireProfile(userId);
        requireCustomerType(profile, CustomerType.INDIVIDUAL);
        requireEditable(profile);
        validateDocumentNumber(request.documentType(), request.documentNumber());

        KycVerification kyc = kycRepository.findByProfileId(profile.getId())
                .orElseGet(() -> KycVerification.builder()
                        .profileId(profile.getId())
                        .status(VerificationStatus.PENDING)
                        .build());

        kyc.setLegalName(request.legalName());
        kyc.setDateOfBirth(request.dateOfBirth());
        kyc.setDocumentType(request.documentType());
        kyc.setDocumentNumber(request.documentNumber());
        kyc.setStreetAddress(request.streetAddress());
        kyc.setDistrict(request.district());
        kyc.setProvinceCity(request.provinceCity());
        if (kyc.getStatus() == null) {
            kyc.setStatus(VerificationStatus.PENDING);
        }
        return kycRepository.save(kyc);
    }

    @Override
    @Transactional
    public VerificationDocument addDocument(Integer userId, AddVerificationDocumentRequest request) {
        CustomerProfile profile = requireProfile(userId);
        requireEditable(profile);
        return documentRepository.save(VerificationDocument.builder()
                .profileId(profile.getId())
                .ownerId(request.ownerId())
                .documentType(request.documentType())
                .storageKey(request.storageKey())
                .originalFilename(request.originalFilename())
                .contentType(request.contentType())
                .sizeBytes(request.sizeBytes())
                .status(DocumentStatus.UPLOADED)
                .build());
    }

    @Override
    @Transactional
    public CustomerProfile submit(Integer userId) {
        CustomerProfile profile = requireProfile(userId);
        requireEditable(profile);
        validateForSubmit(profile);

        OnboardingStatus from = profile.getStatus();
        OnboardingStatusMachine.assertTransition(from, OnboardingStatus.SUBMITTED);
        profile.setStatus(OnboardingStatus.SUBMITTED);
        profile.setSubmittedAt(Instant.now());
        CustomerProfile saved = profileRepository.save(profile);
        auditWriter.record(saved.getId(), userId, OnboardingAuditAction.SUBMIT, from, OnboardingStatus.SUBMITTED, null);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireApproved(Integer userId) {
        CustomerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERROR_403_2302));
        if (profile.getStatus() != OnboardingStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ERROR_403_2302);
        }
    }

    private CustomerProfile requireProfile(Integer userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERROR_404_2300));
    }

    private void requireCustomerType(CustomerProfile profile, CustomerType expected) {
        if (profile.getCustomerType() != expected) {
            throw new BusinessException(
                    ErrorCode.ERROR_400_4000,
                    "Operation requires customer type " + expected
            );
        }
    }

    private void requireEditable(CustomerProfile profile) {
        if (!EDITABLE_STATES.contains(profile.getStatus())) {
            throw new BusinessException(
                    ErrorCode.ERROR_409_2306,
                    "Profile is not editable in status " + profile.getStatus()
            );
        }
    }

    private void validateDocumentNumber(String documentType, String documentNumber) {
        if (CCCD.equals(documentType) && (documentNumber == null || !documentNumber.matches("^[0-9]{12}$"))) {
            throw new BusinessException(
                    ErrorCode.ERROR_400_VALIDATION,
                    "CCCD document number must be exactly 12 digits"
            );
        }
    }

    private void validateForSubmit(CustomerProfile profile) {
        if (profile.getCustomerType() == CustomerType.INDIVIDUAL) {
            KycVerification kyc = kycRepository.findByProfileId(profile.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERROR_400_2303));
            requireKycComplete(kyc);
        }
        boolean hasDocument = !documentRepository.findByProfileId(profile.getId()).isEmpty();
        if (!hasDocument) {
            throw new BusinessException(ErrorCode.ERROR_400_2305);
        }
    }

    private void requireKycComplete(KycVerification kyc) {
        if (isBlank(kyc.getLegalName())
                || kyc.getDateOfBirth() == null
                || isBlank(kyc.getDocumentType())
                || isBlank(kyc.getDocumentNumber())
                || isBlank(kyc.getStreetAddress())
                || isBlank(kyc.getDistrict())
                || isBlank(kyc.getProvinceCity())) {
            throw new BusinessException(ErrorCode.ERROR_400_2303);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
