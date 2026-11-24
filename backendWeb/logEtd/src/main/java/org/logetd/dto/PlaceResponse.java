package org.logetd.dto;

import org.logetd.domain.listing.Place;

public record PlaceResponse(Long id,
                            String label,
                            Long monthlyRentMillimes,
                            Long chargesMillimes,
                            String estimations,
                            Long estimationsMillimes,
                            Long realMonthlyCostMillimes) {

    /** Constructeur historique (Étape 3) conservé pour compatibilité. */
    public PlaceResponse(Long id, String label, Long monthlyRentMillimes, Long chargesMillimes, String estimations) {
        this(id, label, monthlyRentMillimes, chargesMillimes, estimations, 0L,
                nz(monthlyRentMillimes) + nz(chargesMillimes));
    }

    public static PlaceResponse from(Place place) {
        Long estimates = place.getEstimationsMillimes() == null ? 0L : place.getEstimationsMillimes();
        return new PlaceResponse(
                place.getId(),
                place.getLabel(),
                place.getMonthlyRentMillimes(),
                place.getChargesMillimes(),
                place.getEstimations(),
                estimates,
                nz(place.getMonthlyRentMillimes()) + nz(place.getChargesMillimes()) + estimates
        );
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
