package org.logetd.service;

import org.logetd.domain.admin.AuditLog;
import org.logetd.domain.admin.AuditLogRepository;
import org.logetd.domain.admin.Report;
import org.logetd.domain.admin.ReportRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.payment.Payment;
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.AdminDecisionRequest;
import org.logetd.dto.AdminListingResponse;
import org.logetd.dto.AdminQueueResponse;
import org.logetd.dto.AuditLogPageResponse;
import org.logetd.dto.AuditLogResponse;
import org.logetd.dto.LatePaymentResponse;
import org.logetd.dto.ReportResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.AuditAction;
import org.logetd.shared.Audited;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.ReportStatus;
import org.logetd.shared.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Back-office : file de travail, décisions sur les annonces et journal d'audit. */
@Service
@Transactional
public class AdminService {

    public static final int LATE_PAYMENT_AFTER_HOURS = 48;
    public static final int MAX_PAGE_SIZE = 50;

    private final ListingRepository listingRepository;
    private final ReportRepository reportRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminService(ListingRepository listingRepository,
                        ReportRepository reportRepository,
                        PaymentRepository paymentRepository,
                        AuditLogRepository auditLogRepository) {
        this.listingRepository = listingRepository;
        this.reportRepository = reportRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AdminQueueResponse queue() {
        List<AdminListingResponse> pendingListings = listingRepository
                .findByStatusOrderByIdAsc(ListingStatus.PENDING).stream()
                .map(AdminListingResponse::from).toList();
        List<ReportResponse> openReports = reportRepository
                .findByStatusOrderByIdAsc(ReportStatus.OPEN).stream()
                .map(ReportResponse::from).toList();
        Instant cutoff = Instant.now().minusSeconds(LATE_PAYMENT_AFTER_HOURS * 3600L);
        List<LatePaymentResponse> latePayments = paymentRepository
                .findByStatusAndDeclaredAtBefore(PaymentStatus.DECLARED, cutoff).stream()
                .map(LatePaymentResponse::from).toList();
        return new AdminQueueResponse(pendingListings, openReports, latePayments);
    }

    @Audited(action = AuditAction.LISTING_VALIDATED, targetType = "Listing", targetId = "#args[1]")
    public AdminListingResponse validate(User admin, Long listingId) {
        requireAdmin(admin);
        Listing listing = listingForDecision(listingId);
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setLastConfirmedAt(Instant.now());
        return AdminListingResponse.from(listingRepository.save(listing));
    }

    @Audited(action = AuditAction.LISTING_REFUSED, targetType = "Listing",
            targetId = "#args[1]", details = "#args[2].reason()")
    public AdminListingResponse refuse(User admin, Long listingId, AdminDecisionRequest decision) {
        requireAdmin(admin);
        if (decision == null || decision.reason() == null || decision.reason().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUSAL_REASON_REQUIRED",
                    "Le motif du refus est obligatoire.",
                    List.of(new ApiErrorResponse.FieldIssue("reason", "obligatoire")));
        }
        Listing listing = listingForDecision(listingId);
        listing.setStatus(ListingStatus.REFUSED);
        return AdminListingResponse.from(listingRepository.save(listing));
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse audit(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "Page positive ou nulle et taille entre 1 et 50.",
                    List.of(new ApiErrorResponse.FieldIssue("page/size", "valeur invalide")));
        }
        Page<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<AuditLogResponse> content = logs.getContent().stream().map(AuditLogResponse::from).toList();
        return new AuditLogPageResponse(content, page, size,
                logs.getTotalElements(), logs.getTotalPages());
    }

    private Listing listingForDecision(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND",
                        "Annonce introuvable."));
        if (listing.getStatus() != ListingStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "LISTING_STATUS_INVALID",
                    "Seules les annonces en attente peuvent être validées ou refusées.");
        }
        return listing;
    }

    private void requireAdmin(User admin) {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_REQUIRED",
                    "Réservé à l'administrateur.");
        }
    }
}
