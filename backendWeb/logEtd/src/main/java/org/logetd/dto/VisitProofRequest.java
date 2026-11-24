package org.logetd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Code à usage unique reçu du propriétaire, avec position facultative. */
public record VisitProofRequest(
        @NotBlank(message = "Le code de preuve est obligatoire.")
        @Size(max = 32, message = "Le code de preuve ne doit pas dépasser 32 caractères.")
        String proofCode,

        Double latitude,

        Double longitude
) {
}
