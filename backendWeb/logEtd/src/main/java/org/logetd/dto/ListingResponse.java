package org.logetd.dto;

import org.logetd.domain.listing.Listing;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;

import java.util.List;

public record ListingResponse(Long id,
                              String title,
                              String description,
                              String city,
                              String exactAddress,
                              GenderRule genderRule,
                              ListingStatus status,
                              Long ownerId,
                              List<PhotoResponse> photos,
                              List<PlaceResponse> places,
                              String district,
                              boolean furnished,
                              boolean chargesIncluded,
                              boolean internetIncluded) {

    /** Constructeur historique (Étape 3) conservé pour compatibilité. */
    public ListingResponse(Long id, String title, String description, String city, String exactAddress,
                           GenderRule genderRule, ListingStatus status, Long ownerId,
                           List<PhotoResponse> photos, List<PlaceResponse> places) {
        this(id, title, description, city, exactAddress, genderRule, status, ownerId, photos, places,
                null, false, false, false);
    }

    public static ListingResponse from(Listing listing,
                                       List<PhotoResponse> photos,
                                       List<PlaceResponse> places) {
        return new ListingResponse(
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCity(),
                listing.getExactAddress(),
                listing.getGenderRule(),
                listing.getStatus(),
                listing.getOwner() == null ? null : listing.getOwner().getId(),
                photos,
                places,
                listing.getDistrict(),
                listing.isFurnished(),
                listing.isChargesIncluded(),
                listing.isInternetIncluded()
        );
    }
}
