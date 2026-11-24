package org.logetd.dto;

import java.util.List;

/** File de travail du back-office : annonces à valider, signalements ouverts, paiements en retard. */
public record AdminQueueResponse(List<AdminListingResponse> pendingListings,
                                 List<ReportResponse> openReports,
                                 List<LatePaymentResponse> latePayments) {
}
