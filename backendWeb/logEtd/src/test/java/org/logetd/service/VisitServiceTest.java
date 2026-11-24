package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.booking.Visit;
import org.logetd.domain.booking.VisitRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.VisitProofRequest;
import org.logetd.dto.VisitRequest;
import org.logetd.dto.VisitResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.VisitStatus;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock
    private VisitRepository visitRepository;
    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private VisitService service;

    private final User tenant = user(1L, Role.TENANT);
    private final User owner = user(2L, Role.OWNER);

    @Test
    void requestCreatesVisitInRequestedStatus() {
        Listing listing = listing(10L, ListingStatus.PENDING);
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(visitRepository.save(any(Visit.class))).thenAnswer(inv -> {
            Visit saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        VisitResponse response = service.request(tenant, new VisitRequest(10L, future()));

        assertEquals(100L, response.id());
        assertEquals(VisitStatus.REQUESTED, response.status());
        assertEquals(10L, response.listingId());
        assertEquals(1L, response.tenantId());
        assertEquals(2L, response.ownerId());
        assertNull(response.proofCode(), "le code ne doit pas être exposé à l'étudiant");
    }

    @Test
    void requestRejectsOwnerRole() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(owner, new VisitRequest(10L, future())));

        assertEquals(403, ex.getStatus().value());
        assertEquals("TENANT_ROLE_REQUIRED", ex.getCode());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void requestRejectsDraftListing() {
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, ListingStatus.DRAFT)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(tenant, new VisitRequest(10L, future())));

        assertEquals("LISTING_NOT_VISITABLE", ex.getCode());
        verifyNoInteractions(visitRepository);
    }

    @Test
    void requestRejectsPastDate() {
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, ListingStatus.PUBLISHED)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(tenant, new VisitRequest(10L, Instant.now().minus(1, ChronoUnit.DAYS))));

        assertEquals("INVALID_VISIT_DATE", ex.getCode());
        verifyNoInteractions(visitRepository);
    }

    @Test
    void confirmGeneratesSixDigitCodeVisibleOnlyToOwner() {
        Visit visit = visit(VisitStatus.REQUESTED, null);
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));
        when(visitRepository.save(visit)).thenReturn(visit);

        VisitResponse response = service.confirm(owner, 100L);

        assertEquals(VisitStatus.CONFIRMED, response.status());
        assertNotNull(response.proofCode());
        assertEquals(6, response.proofCode().length());
        assertTrue(response.proofCode().matches("\\d{6}"));
    }

    @Test
    void confirmRejectsAnotherOwner() {
        Visit visit = visit(VisitStatus.REQUESTED, null);
        visit.setOwner(user(99L, Role.OWNER));
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(owner, 100L));

        assertEquals("VISIT_NOT_OWNED", ex.getCode());
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void confirmRejectsAlreadyConfirmedVisit() {
        Visit visit = visit(VisitStatus.CONFIRMED, "123456");
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(owner, 100L));

        assertEquals("VISIT_ALREADY_CONFIRMED", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void proveAcceptsCodeClearsItAndStoresPosition() {
        Visit visit = visit(VisitStatus.CONFIRMED, "123456");
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));
        when(visitRepository.save(visit)).thenReturn(visit);

        VisitResponse response = service.prove(tenant, 100L, new VisitProofRequest("123456", 35.8, 10.6));

        assertEquals(VisitStatus.PROVEN, response.status());
        assertNotNull(response.provenAt());
        assertNull(response.proofCode());
        assertNull(visit.getProofCode(), "le code doit être à usage unique");
        assertEquals(35.8, visit.getProofLatitude());
        assertEquals(10.6, visit.getProofLongitude());
    }

    @Test
    void proveRejectsWrongCode() {
        Visit visit = visit(VisitStatus.CONFIRMED, "123456");
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.prove(tenant, 100L, new VisitProofRequest("000000", null, null)));

        assertEquals("INVALID_PROOF_CODE", ex.getCode());
        assertEquals(400, ex.getStatus().value());
        assertEquals(VisitStatus.CONFIRMED, visit.getStatus());
    }

    @Test
    void proveRejectsAnotherTenant() {
        Visit visit = visit(VisitStatus.CONFIRMED, "123456");
        visit.setTenant(user(99L, Role.TENANT));
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.prove(tenant, 100L, new VisitProofRequest("123456", null, null)));

        assertEquals("VISIT_NOT_FOR_TENANT", ex.getCode());
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void proveRejectsAlreadyProvenVisit() {
        Visit visit = visit(VisitStatus.PROVEN, null);
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.prove(tenant, 100L, new VisitProofRequest("123456", null, null)));

        assertEquals("VISIT_NOT_CONFIRMED", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    private Instant future() {
        return Instant.now().plus(2, ChronoUnit.DAYS);
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Listing listing(Long id, ListingStatus status) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setOwner(owner);
        listing.setStatus(status);
        return listing;
    }

    private Visit visit(VisitStatus status, String proofCode) {
        Visit visit = new Visit();
        visit.setId(100L);
        visit.setListing(listing(10L, ListingStatus.PUBLISHED));
        visit.setTenant(tenant);
        visit.setOwner(owner);
        visit.setScheduledAt(future());
        visit.setStatus(status);
        visit.setProofCode(proofCode);
        return visit;
    }
}
