package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.admin.Report;
import org.logetd.domain.admin.ReportRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.ReportRequest;
import org.logetd.dto.ReportResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.AuditAction;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.ReportReason;
import org.logetd.shared.ReportStatus;
import org.logetd.shared.Role;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReportService service;

    private final Listing listing = listing();

    @Test
    void firstReportCreatesOpenReportWithoutSuspending() {
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });
        when(reportRepository.countDistinctReportersByListingId(5L)).thenReturn(1L);

        ReportResponse response = service.create(user(1L, Role.TENANT),
                new ReportRequest(5L, ReportReason.FAKE_LISTING, "Annonce suspecte"));

        assertEquals(20L, response.id());
        assertEquals(ReportStatus.OPEN, response.status());
        assertEquals(ListingStatus.PENDING, listing.getStatus());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void thirdDistinctReportSuspendsListingAndWritesSystemAudit() {
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(23L);
            return saved;
        });
        when(reportRepository.countDistinctReportersByListingId(5L)).thenReturn(3L);

        service.create(user(3L, Role.TENANT), new ReportRequest(5L, ReportReason.SCAM, null));

        assertEquals(ListingStatus.SUSPENDED, listing.getStatus());
        verify(listingRepository).save(listing);
        verify(auditService).log(AuditAction.LISTING_SUSPENDED_BY_REPORT, "Listing", 5L,
                null, "distinctReports=3");
    }

    @Test
    void secondDistinctReportDoesNotSuspend() {
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(22L);
            return saved;
        });
        when(reportRepository.countDistinctReportersByListingId(5L)).thenReturn(2L);

        service.create(user(2L, Role.TENANT), new ReportRequest(5L, ReportReason.OTHER, null));

        assertEquals(ListingStatus.PENDING, listing.getStatus());
        verify(listingRepository, never()).save(any());
    }

    @Test
    void ownerCannotReportOwnListing() {
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(user(9L, Role.OWNER),
                        new ReportRequest(5L, ReportReason.FAKE_LISTING, null)));

        assertEquals("REPORT_OWN_LISTING", ex.getCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void unknownListingReturns404() {
        when(listingRepository.findById(6L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(user(1L, Role.TENANT),
                        new ReportRequest(6L, ReportReason.SCAM, null)));

        assertEquals("LISTING_NOT_FOUND", ex.getCode());
    }

    private Listing listing() {
        Listing listing = new Listing();
        listing.setId(5L);
        listing.setOwner(user(9L, Role.OWNER));
        listing.setStatus(ListingStatus.PENDING);
        return listing;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
