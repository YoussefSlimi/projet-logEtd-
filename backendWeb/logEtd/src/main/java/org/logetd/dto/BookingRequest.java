package org.logetd.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record BookingRequest(
        @NotNull(message = "L'identifiant de la place est obligatoire.")
        Long placeId,

        @NotNull(message = "La date de début est obligatoire.")
        LocalDate startDate,

        @NotNull(message = "La date de fin est obligatoire.")
        LocalDate endDate,

        @PositiveOrZero(message = "L'acompte ne peut pas être négatif.")
        Long depositMillimes
) {
}
