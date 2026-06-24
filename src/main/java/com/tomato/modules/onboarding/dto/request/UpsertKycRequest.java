package com.tomato.modules.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

@Schema(description = "Request body to create or update individual KYC details")
public record UpsertKycRequest(
        @Schema(description = "Legal full name as on the identity document", example = "Nguyen Van A")
        @NotBlank(message = "Legal name is required")
        String legalName,

        @Schema(description = "Date of birth", example = "1990-01-15")
        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Schema(description = "Identity document type", example = "CCCD", allowableValues = {"CCCD", "PASSPORT"})
        @NotBlank(message = "Document type is required")
        String documentType,

        // CCCD must be exactly 12 digits, but PASSPORT shares this field, so the
        // document-type-specific format is enforced in the service layer, not here.
        @Schema(description = "Identity document number", example = "012345678901")
        @NotBlank(message = "Document number is required")
        String documentNumber,

        @Schema(description = "Street address", example = "123 Le Loi")
        @NotBlank(message = "Street address is required")
        String streetAddress,

        @Schema(description = "District", example = "District 1")
        @NotBlank(message = "District is required")
        String district,

        @Schema(description = "Province or city", example = "Ho Chi Minh City")
        @NotBlank(message = "Province/city is required")
        String provinceCity
) {
}
