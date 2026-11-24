package org.logetd.dto;

import org.logetd.domain.admin.Report;
import org.logetd.shared.ReportReason;
import org.logetd.shared.ReportStatus;

import java.time.Instant;

public record ReportResponse(Long id,
                             Long listingId,
                             Long reporterId,
                             ReportReason reason,
                             String details,
                             ReportStatus status,
                             Instant createdAt) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getListing().getId(),
                report.getReporter().getId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
