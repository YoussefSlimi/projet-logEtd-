package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.admin.AuditLog;
import org.logetd.domain.admin.AuditLogRepository;
import org.logetd.domain.admin.Report;
import org.logetd.domain.admin.ReportRepository;
import org.logetd.domain.booking.Booking;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.payment.Payment;
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.AdminDecisionRequest;
import org.logetd.dto.AdminListingResponse;
import org.logetd.dto.AdminQueueResponse;
import org.logetd.dto.AuditLogPageResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.AuditAction;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;
import org.logetd.shared.ReportReason;
import org.logetd.shared.ReportStatus;
import org.logetd.shared.Role;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminService service;

    private final User admin = user(100L, Role.ADMIN);

    @Test
    void validatePublishesPendingListing() {
        Listing listing = listing(ListingStatus.PENDING);
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(listingRepository.save(listing)).thenReturn(listing);

        AdminListingResponse response = service.validate(admin, 5L);

        assertEquals(ListingStatus.PUBLISHED, response.status());
        assertNotNull(listing.getLastConfirmedAt());
    }

    @Test
    void validateRejectsNonPendingListing() {
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing(ListingStatus.PUBLISHED)));

        ApiException ex = assertThrows(ApiException.class, () -> service.validate(admin, 5L));

        assertEquals("LISTING_STATUS_INVALID", ex.getCode());
        assertEquals(409, ex.getStatus().value());
        verify(listingRepository, never()).save(any());
    }

    @Test
    void refuseSetsRefusedStatus() {
        Listing listing = listing(ListingStatus.PENDING);
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(listingRepository.save(listing)).thenReturn(listing);

        AdminListingResponse response = service.refuse(admin, 5L,
                new AdminDecisionRequest("Photos trompeuses"));

        assertEquals(ListingStatus.REFUSED, response.status());
        verify(listingRepository).save(listing);
    }

    @Test
    void refuseRequiresReason() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.refuse(admin, 5L, new AdminDecisionRequest("  ")));

        assertEquals("REFUSAL_REASON_REQUIRED", ex.getCode());
        verify(listingRepository, never()).findById(any());
    }

    @Test
    void nonAdminCannotDecide() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.validate(user(1L, Role.TENANT), 5L));

        assertEquals("ADMIN_ROLE_REQUIRED", ex.getCode());
    }

    @Test
    void queueContainsPendingListingsOpenReportsAndLatePayments() {
        when(listingRepository.findByStatusOrderByIdAsc(ListingStatus.PENDING))
                .thenReturn(List.of(listing(ListingStatus.PENDING)));
        when(reportRepository.findByStatusOrderByIdAsc(ReportStatus.OPEN))
                .thenReturn(List.of(report()));
        when(paymentRepository.findByStatusAndDeclaredAtBefore(
                org.mockito.ArgumentMatchers.eq(PaymentStatus.DECLARED), any(Instant.class)))
                .thenReturn(List.of(latePayment()));

        AdminQueueResponse queue = service.queue();

        assertEquals(1, queue.pendingListings().size());
        assertEquals(1, queue.openReports().size());
        assertEquals(1, queue.latePayments().size());
        assertEquals(5L, queue.pendingListings().get(0).id());
        assertEquals(ReportReason.FAKE_LISTING, queue.openReports().get(0).reason());
        assertEquals(30L, queue.latePayments().get(0).id());
    }

    @Test
    void auditReturnsSortedPage() {
        AuditLog entry = new AuditLog();
        entry.setId(3L);
        entry.setActorId(100L);
        entry.setAction(AuditAction.LISTING_VALIDATED);
        entry.setTargetType("Listing");
        entry.setTargetId(5L);
        entry.setCreatedAt(Instant.parse("2026-11-24T12:00:00Z"));
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        AuditLogPageResponse page = service.audit(0, 20);

        assertEquals(1, page.content().size());
        assertEquals(AuditAction.LISTING_VALIDATED, page.content().get(0).action());
        assertEquals(1, page.totalElements());
    }

    @Test
    void auditRejectsTooLargePage() {
        ApiException ex = assertThrows(ApiException.class, () -> service.audit(0, 51));

        assertEquals("INVALID_PAGE", ex.getCode());
    }

    private Listing listing(ListingStatus status) {
        Listing listing = new Listing();
        listing.setId(5L);
        listing.setTitle("Studio");
        listing.setCity("Monastir");
        listing.setOwner(user(2L, Role.OWNER));
        listing.setStatus(status);
        return listing;
    }

    private Report report() {
        Report report = new Report();
        report.setId(20L);
        report.setListing(listing(ListingStatus.PENDING));
        report.setReporter(user(1L, Role.TENANT));
        report.setReason(ReportReason.FAKE_LISTING);
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(Instant.now());
        return report;
    }

    private Payment latePayment() {
        Payment payment = new Payment();
        payment.setId(30L);
        Booking booking = new Booking();
        booking.setId(7L);
        payment.setBooking(booking);
        payment.setPaymentType(PaymentType.DEPOSIT);
        payment.setAmountMillimes(50000L);
        payment.setStatus(PaymentStatus.DECLARED);
        payment.setDeclaredAt(Instant.now().minusSeconds(49 * 3600L));
        return payment;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
