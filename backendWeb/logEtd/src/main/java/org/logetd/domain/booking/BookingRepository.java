package org.logetd.domain.booking;

import jakarta.persistence.LockModeType;
import org.logetd.shared.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPlaceId(Long placeId);

    /** Verrou pessimiste : sérialise les déclarations de paiement d'une même réservation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /** Places ayant une réservation active qui chevauche la période demandée. */
    @Query("""
            select distinct b.place.id from Booking b
            where b.status in :statuses
              and b.startDate <= :endDate
              and b.endDate >= :startDate
            """)
    Set<Long> findBookedPlaceIds(@Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate,
                                 @Param("statuses") Collection<BookingStatus> statuses);

    /** Chevauchement avec une réservation bloquante (demande en attente ou statut actif). */
    @Query("""
            select case when count(b) > 0 then true else false end from Booking b
            where b.place.id = :placeId
              and b.status in :statuses
              and b.startDate <= :endDate
              and b.endDate >= :startDate
            """)
    boolean existsOverlapping(@Param("placeId") Long placeId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("statuses") Collection<BookingStatus> statuses);
}
