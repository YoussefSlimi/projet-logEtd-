package org.logetd.domain.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {

    List<ListingPhoto> findByListingIdOrderBySortOrderAsc(Long listingId);

    long countByListingId(Long listingId);

    List<ListingPhoto> findByListingIdInOrderBySortOrderAsc(List<Long> listingIds);
}
