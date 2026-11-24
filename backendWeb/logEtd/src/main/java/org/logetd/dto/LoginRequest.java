package org.logetd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** L'identifiant peut être l'email ou le numéro de téléphone. */
public record LoginRequest(
        @NotBlank(message = "L'identifiant est obligatoire.")
        @Size(max = 255, message = "L'identifiant ne doit pas dépasser 255 caractères.")
        String identifier,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        String password
) {
}
