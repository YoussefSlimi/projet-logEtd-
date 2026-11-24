package org.logetd.domain.listing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByListingIdOrderByIdAsc(Long listingId);

    List<Place> findByListingIdInOrderByListingIdAscIdAsc(List<Long> listingIds);

    /** Verrou pessimiste sur la place : sérialise les demandes de réservation concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Place p where p.id = :id")
    Optional<Place> findByIdForUpdate(@Param("id") Long id);
}
