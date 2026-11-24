package org.logetd.dto;

import org.logetd.domain.booking.Visit;
import org.logetd.shared.VisitStatus;

import java.time.Instant;

public record VisitResponse(Long id,
                            Long listingId,
                            Long tenantId,
                            Long ownerId,
                            Instant scheduledAt,
                            VisitStatus status,
                            Instant provenAt,
                            String proofCode) {

    public static VisitResponse from(Visit visit, boolean revealProofCode) {
        return new VisitResponse(
                visit.getId(),
                visit.getListing() == null ? null : visit.getListing().getId(),
                visit.getTenant() == null ? null : visit.getTenant().getId(),
                visit.getOwner() == null ? null : visit.getOwner().getId(),
                visit.getScheduledAt(),
                visit.getStatus(),
                visit.getProvenAt(),
                revealProofCode ? visit.getProofCode() : null
        );
    }
}
