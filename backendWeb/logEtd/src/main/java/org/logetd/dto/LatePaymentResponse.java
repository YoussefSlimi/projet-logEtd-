package org.logetd.dto;

import org.logetd.domain.payment.Payment;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;

import java.time.Instant;

public record LatePaymentResponse(Long id,
                                  Long bookingId,
                                  PaymentType paymentType,
                                  Long amountMillimes,
                                  PaymentStatus status,
                                  Instant declaredAt) {

    public static LatePaymentResponse from(Payment payment) {
        return new LatePaymentResponse(
                payment.getId(), payment.getBooking().getId(), payment.getPaymentType(),
                payment.getAmountMillimes(), payment.getStatus(), payment.getDeclaredAt());
    }
}
