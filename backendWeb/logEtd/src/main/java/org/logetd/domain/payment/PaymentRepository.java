package org.logetd.domain.payment;

import jakarta.persistence.LockModeType;
import org.logetd.shared.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Verrou pessimiste : sérialise deux confirmations concurrentes du même paiement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByDeclarationIdempotencyHash(String hash);

    Optional<Payment> findByConfirmationIdempotencyHash(String hash);

    Page<Payment> findByBookingIdOrderByIdAsc(Long bookingId, Pageable pageable);

    /** Paiements déclarés depuis plus de 48 h sans confirmation propriétaire. */
    List<Payment> findByStatusAndDeclaredAtBefore(PaymentStatus status, Instant cutoff);
}
