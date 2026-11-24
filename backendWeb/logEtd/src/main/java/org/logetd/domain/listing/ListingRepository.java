package org.logetd.domain.listing;

import org.logetd.shared.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    long countByOwnerId(Long ownerId);

    /** File d'attente du back-office : annonces soumises à validation. */
    List<Listing> findByStatusOrderByIdAsc(ListingStatus status);
}
