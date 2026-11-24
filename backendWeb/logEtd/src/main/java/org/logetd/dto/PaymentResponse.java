package org.logetd.dto;

import org.logetd.domain.payment.Payment;
import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;

import java.time.Instant;

public record PaymentResponse(Long id,
                              Long bookingId,
                              Long tenantId,
                              PaymentType paymentType,
                              PaymentMethod paymentMethod,
                              Long amountMillimes,
                              PaymentStatus status,
                              boolean tenantDeclared,
                              boolean ownerConfirmed,
                              Instant declaredAt,
                              Instant confirmedAt,
                              String proofPath) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBooking() == null ? null : payment.getBooking().getId(),
                payment.getBooking() == null || payment.getBooking().getTenant() == null
                        ? null : payment.getBooking().getTenant().getId(),
                payment.getPaymentType(),
                payment.getPaymentMethod(),
                payment.getAmountMillimes(),
                payment.getStatus(),
                payment.isTenantDeclared(),
                payment.isOwnerConfirmed(),
                payment.getDeclaredAt(),
                payment.getConfirmedAt(),
                payment.getProofPath()
        );
    }
}
