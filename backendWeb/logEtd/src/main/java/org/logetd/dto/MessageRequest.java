package org.logetd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps d'un message envoyé via WebSocket ou via le repli REST. */
public record MessageRequest(
        @NotNull(message = "L'identifiant de la réservation est obligatoire.")
        Long bookingId,

        @NotBlank(message = "Le contenu du message est obligatoire.")
        @Size(max = 2000, message = "Le message ne doit pas dépasser 2000 caractères.")
        String content) {
}
