package org.logetd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.logetd.shared.GenderRule;

public record ListingRequest(
        @NotBlank(message = "Le titre est obligatoire.")
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères.")
        String title,

        @Size(max = 4000, message = "La description ne doit pas dépasser 4000 caractères.")
        String description,

        @Size(max = 80, message = "La ville ne doit pas dépasser 80 caractères.")
        String city,

        @Size(max = 500, message = "L'adresse ne doit pas dépasser 500 caractères.")
        String exactAddress,

        GenderRule genderRule,

        @Size(max = 80, message = "Le quartier ne doit pas dépasser 80 caractères.")
        String district,

        Boolean furnished,
        Boolean chargesIncluded,
        Boolean internetIncluded
) {
    /** Constructeur historique (Étape 3) conservé pour compatibilité. */
    public ListingRequest(String title, String description, String city, String exactAddress, GenderRule genderRule) {
        this(title, description, city, exactAddress, genderRule, null, false, false, false);
    }
}
