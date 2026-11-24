package org.logetd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motif obligatoire lors d'un refus d'annonce. */
public record AdminDecisionRequest(
        @NotBlank(message = "Le motif du refus est obligatoire.")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères.")
        String reason) {
}
