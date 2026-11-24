package org.logetd.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.logetd.domain.booking.Booking;
import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;

import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "amount_millimes", nullable = false)
    private Long amountMillimes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "tenant_declared", nullable = false)
    private boolean tenantDeclared;

    @Column(name = "owner_confirmed", nullable = false)
    private boolean ownerConfirmed;

    /** Nom du fichier de preuve généré, stocké hors base comme les photos d'annonces. */
    @Column(name = "proof_path", length = 255)
    private String proofPath;

    @Column(name = "declared_at")
    private Instant declaredAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** SHA-256 de (Idempotency-Key + identifiant de la réservation), unique. */
    @Column(name = "declaration_idempotency_hash", length = 64, unique = true)
    private String declarationIdempotencyHash;

    /** SHA-256 de (Idempotency-Key + identifiant du paiement), unique. */
    @Column(name = "confirmation_idempotency_hash", length = 64, unique = true)
    private String confirmationIdempotencyHash;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getAmountMillimes() {
        return amountMillimes;
    }

    public void setAmountMillimes(Long amountMillimes) {
        this.amountMillimes = amountMillimes;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public boolean isTenantDeclared() {
        return tenantDeclared;
    }

    public void setTenantDeclared(boolean tenantDeclared) {
        this.tenantDeclared = tenantDeclared;
    }

    public boolean isOwnerConfirmed() {
        return ownerConfirmed;
    }

    public void setOwnerConfirmed(boolean ownerConfirmed) {
        this.ownerConfirmed = ownerConfirmed;
    }

    public String getProofPath() {
        return proofPath;
    }

    public void setProofPath(String proofPath) {
        this.proofPath = proofPath;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(Instant declaredAt) {
        this.declaredAt = declaredAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getDeclarationIdempotencyHash() {
        return declarationIdempotencyHash;
    }

    public void setDeclarationIdempotencyHash(String declarationIdempotencyHash) {
        this.declarationIdempotencyHash = declarationIdempotencyHash;
    }

    public String getConfirmationIdempotencyHash() {
        return confirmationIdempotencyHash;
    }

    public void setConfirmationIdempotencyHash(String confirmationIdempotencyHash) {
        this.confirmationIdempotencyHash = confirmationIdempotencyHash;
    }
}
