package org.logetd.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.booking.Booking;
import org.logetd.domain.payment.Payment;
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.shared.PaymentStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAlertSchedulerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentAlertScheduler scheduler;

    @Test
    void alertsOnlyPaymentsDeclaredMoreThan48HoursAgo() {
        Payment late = payment(5L, 7L);
        when(paymentRepository.findByStatusAndDeclaredAtBefore(eq(PaymentStatus.DECLARED), any(Instant.class)))
                .thenReturn(List.of(late));

        List<Long> alerted = scheduler.alertUnconfirmedPayments();

        assertEquals(List.of(5L), alerted);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(paymentRepository).findByStatusAndDeclaredAtBefore(eq(PaymentStatus.DECLARED), cutoff.capture());
        Duration age = Duration.between(cutoff.getValue(), Instant.now());
        assertTrue(age.toHours() >= 47 && age.toHours() <= 49, "seuil attendu ≈ 48 h : " + age);
    }

    @Test
    void doesNothingWhenNoPaymentIsLate() {
        when(paymentRepository.findByStatusAndDeclaredAtBefore(eq(PaymentStatus.DECLARED), any(Instant.class)))
                .thenReturn(List.of());

        assertTrue(scheduler.alertUnconfirmedPayments().isEmpty());
    }

    private Payment payment(Long id, Long bookingId) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        Payment payment = new Payment();
        payment.setId(id);
        payment.setBooking(booking);
        payment.setStatus(PaymentStatus.DECLARED);
        payment.setDeclaredAt(Instant.now().minus(Duration.ofHours(49)));
        return payment;
    }
}
