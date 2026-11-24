package org.logetd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PlaceRequest(
        @NotBlank(message = "Le libellé est obligatoire.")
        @Size(max = 120, message = "Le libellé ne doit pas dépasser 120 caractères.")
        String label,

        @NotNull(message = "Le loyer mensuel est obligatoire.")
        @PositiveOrZero(message = "Le loyer mensuel ne peut pas être négatif.")
        Long monthlyRentMillimes,

        @NotNull(message = "Les charges sont obligatoires.")
        @PositiveOrZero(message = "Les charges ne peuvent pas être négatives.")
        Long chargesMillimes,

        @Size(max = 2000, message = "Les estimations ne doivent pas dépasser 2000 caractères.")
        String estimations,

        @PositiveOrZero(message = "Les estimations chiffrées ne peuvent pas être négatives.")
        Long estimationsMillimes
) {
    /** Constructeur historique (Étape 3) conservé pour compatibilité. */
    public PlaceRequest(String label, Long monthlyRentMillimes, Long chargesMillimes, String estimations) {
        this(label, monthlyRentMillimes, chargesMillimes, estimations, 0L);
    }
}
