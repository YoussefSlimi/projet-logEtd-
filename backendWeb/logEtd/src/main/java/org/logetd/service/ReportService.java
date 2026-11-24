package org.logetd.service;

import org.logetd.domain.admin.Report;
import org.logetd.domain.admin.ReportRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.ReportRequest;
import org.logetd.dto.ReportResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.AuditAction;
import org.logetd.shared.Audited;
import org.logetd.shared.ListingStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Signalements d'annonces avec suspension automatique après 3 signalants distincts. */
@Service
@Transactional
public class ReportService {

    public static final int SUSPENSION_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final ListingRepository listingRepository;
    private final AuditService auditService;

    public ReportService(ReportRepository reportRepository,
                         ListingRepository listingRepository,
                         AuditService auditService) {
        this.reportRepository = reportRepository;
        this.listingRepository = listingRepository;
        this.auditService = auditService;
    }

    @Audited(action = AuditAction.REPORT_CREATED, targetType = "Report", targetId = "#result.id()")
    public ReportResponse create(User reporter, ReportRequest request) {
        if (request == null || request.listingId() == null || request.reason() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT",
                    "Annonce et motif de signalement obligatoires.",
                    List.of(new ApiErrorResponse.FieldIssue("listingId", "valeur invalide")));
        }
        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND",
                        "Annonce introuvable."));
        if (reporter != null && listing.getOwner() != null
                && Objects.equals(reporter.getId(), listing.getOwner().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_OWN_LISTING",
                    "Vous ne pouvez pas signaler votre propre annonce.");
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setListing(listing);
        report.setReason(request.reason());
        report.setDetails(request.details());
        Report saved = reportRepository.save(report);
        ReportResponse response = ReportResponse.from(saved);

        long distinctReporters = reportRepository.countDistinctReportersByListingId(listing.getId());
        if (distinctReporters >= SUSPENSION_THRESHOLD
                && listing.getStatus() != ListingStatus.SUSPENDED) {
            listing.setStatus(ListingStatus.SUSPENDED);
            listingRepository.save(listing);
            auditService.log(AuditAction.LISTING_SUSPENDED_BY_REPORT, "Listing", listing.getId(),
                    null, "distinctReports=" + distinctReporters);
        }
        return response;
    }
}
