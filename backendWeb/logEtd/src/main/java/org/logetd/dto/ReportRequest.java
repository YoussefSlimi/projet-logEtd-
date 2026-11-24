package org.logetd.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.logetd.shared.ReportReason;

public record ReportRequest(
        @NotNull(message = "L'identifiant de l'annonce est obligatoire.")
        Long listingId,

        @NotNull(message = "Le motif est obligatoire.")
        ReportReason reason,

        @Size(max = 1000, message = "Les détails ne doivent pas dépasser 1000 caractères.")
        String details) {
}
