package org.logetd.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.logetd.shared.Role;

public record RegisterRequest(
        @NotBlank(message = "Le téléphone est obligatoire.")
        @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères.")
        String phone,

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email doit être valide.")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères.")
        String password,

        @NotBlank(message = "Le nom complet est obligatoire.")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères.")
        String fullName,

        @NotNull(message = "Le rôle est obligatoire.")
        Role role
) {
}
