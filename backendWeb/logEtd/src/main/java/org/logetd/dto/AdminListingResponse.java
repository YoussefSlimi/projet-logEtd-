package org.logetd.dto;

import org.logetd.domain.listing.Listing;
import org.logetd.shared.ListingStatus;

/** Vue back-office : l'adresse exacte est visible par l'administrateur. */
public record AdminListingResponse(Long id,
                                   String title,
                                   String city,
                                   String district,
                                   String exactAddress,
                                   Long ownerId,
                                   ListingStatus status) {

    public static AdminListingResponse from(Listing listing) {
        return new AdminListingResponse(
                listing.getId(), listing.getTitle(), listing.getCity(), listing.getDistrict(),
                listing.getExactAddress(), listing.getOwner().getId(), listing.getStatus());
    }
}
