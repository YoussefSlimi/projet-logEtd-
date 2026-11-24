package org.logetd.dto;

import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentType;

/** Données scalaires d'une déclaration de paiement (la preuve est transmise à part). */
public record PaymentDeclaration(Long bookingId,
                                 PaymentType paymentType,
                                 PaymentMethod paymentMethod,
                                 Long amountMillimes) {
}
