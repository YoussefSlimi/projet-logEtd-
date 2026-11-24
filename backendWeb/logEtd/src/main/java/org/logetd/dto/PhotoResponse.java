package org.logetd.dto;

import org.logetd.domain.listing.ListingPhoto;

public record PhotoResponse(Long id,
                            String path,
                            boolean exterior,
                            int sortOrder) {

    public static PhotoResponse from(ListingPhoto photo) {
        return new PhotoResponse(photo.getId(), photo.getPath(), photo.isExterior(), photo.getSortOrder());
    }
}
