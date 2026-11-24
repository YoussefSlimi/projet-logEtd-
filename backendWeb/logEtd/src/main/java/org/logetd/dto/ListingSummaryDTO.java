package org.logetd.dto;

import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;

import java.util.List;

/** Projection légère d'une annonce pour la recherche : pas d'adresse exacte, pas d'entité JPA. */
public record ListingSummaryDTO(Long id,
                                String title,
                                String city,
                                String district,
                                GenderRule genderRule,
                                ListingStatus status,
                                String photoPath,
                                Long monthlyCostFromMillimes,
                                List<PlaceSummaryDTO> places) {
}
