package org.logetd.job;

import org.logetd.domain.payment.Payment;
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.shared.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Alerte administrative pour les paiements déclarés depuis plus de 48 h sans confirmation. */
@Component
public class PaymentAlertScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentAlertScheduler.class);

    private final PaymentRepository paymentRepository;

    public PaymentAlertScheduler(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Scheduled(fixedDelayString = "${app.payment.alert-check-interval-ms:3600000}")
    @Transactional(readOnly = true)
    public List<Long> alertUnconfirmedPayments() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(48));
        List<Payment> latePayments = paymentRepository.findByStatusAndDeclaredAtBefore(
                PaymentStatus.DECLARED, cutoff);
        for (Payment payment : latePayments) {
            // Aucune donnée personnelle : uniquement des identifiants techniques.
            LOG.warn("ALERTE_ADMIN PAYMENT_UNCONFIRMED_48H paymentId={} bookingId={}",
                    payment.getId(), payment.getBooking() == null ? null : payment.getBooking().getId());
        }
        return latePayments.stream().map(Payment::getId).toList();
    }
}
