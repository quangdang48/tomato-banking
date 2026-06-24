package com.tomato.modules.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request body to attach verification document metadata")
public record AddVerificationDocumentRequest(
        @Schema(description = "Document type", example = "ID_FRONT")
        @NotBlank(message = "Document type is required")
        String documentType,

        @Schema(description = "Opaque storage reference. Treated as sensitive; never a public URL.")
        @NotBlank(message = "Storage key is required")
        String storageKey,

        @Schema(description = "Original uploaded filename", example = "id-front.jpg")
        @NotBlank(message = "Original filename is required")
        String originalFilename,

        @Schema(description = "MIME content type", example = "image/jpeg")
        @NotBlank(message = "Content type is required")
        String contentType,

        @Schema(description = "File size in bytes", example = "204800")
        @Positive(message = "Size must be positive")
        long sizeBytes,

        @Schema(description = "Optional owner id when the document belongs to a beneficial owner")
        Long ownerId
) {
}
