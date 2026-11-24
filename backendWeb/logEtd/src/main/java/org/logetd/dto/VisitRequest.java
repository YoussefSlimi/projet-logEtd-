package org.logetd.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record VisitRequest(
        @NotNull(message = "L'identifiant de l'annonce est obligatoire.")
        Long listingId,

        @NotNull(message = "La date de visite est obligatoire.")
        Instant scheduledAt
) {
}
