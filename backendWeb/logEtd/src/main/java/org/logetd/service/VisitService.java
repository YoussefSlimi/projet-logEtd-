package org.logetd.service;

import org.logetd.domain.booking.Visit;
import org.logetd.domain.booking.VisitRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.VisitProofRequest;
import org.logetd.dto.VisitRequest;
import org.logetd.dto.VisitResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.VisitStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class VisitService {

    private static final List<ListingStatus> VISITABLE_STATUSES = List.of(
            ListingStatus.PUBLISHED, ListingStatus.PENDING);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VisitRepository visitRepository;
    private final ListingRepository listingRepository;

    public VisitService(VisitRepository visitRepository, ListingRepository listingRepository) {
        this.visitRepository = visitRepository;
        this.listingRepository = listingRepository;
    }

    public VisitResponse request(User tenant, VisitRequest request) {
        requireRole(tenant, Role.TENANT, "TENANT_ROLE_REQUIRED",
                "Seuls les étudiants peuvent demander une visite.");

        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND",
                        "Annonce introuvable."));
        if (!VISITABLE_STATUSES.contains(listing.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LISTING_NOT_VISITABLE",
                    "Cette annonce n'accepte pas de visites.",
                    List.of(new ApiErrorResponse.FieldIssue("listingId", "annonce non visible")));
        }
        if (request.scheduledAt() == null || !request.scheduledAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISIT_DATE",
                    "La visite doit être planifiée dans le futur.",
                    List.of(new ApiErrorResponse.FieldIssue("scheduledAt", "date future requise")));
        }

        Visit visit = new Visit();
        visit.setListing(listing);
        visit.setTenant(tenant);
        visit.setOwner(listing.getOwner());
        visit.setScheduledAt(request.scheduledAt());
        visit.setStatus(VisitStatus.REQUESTED);
        return VisitResponse.from(visitRepository.save(visit), false);
    }

    /** Le code n'est révélé qu'au propriétaire, une seule fois. */
    public VisitResponse confirm(User owner, Long visitId) {
        requireRole(owner, Role.OWNER, "OWNER_ROLE_REQUIRED",
                "Seuls les propriétaires peuvent confirmer une visite.");
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VISIT_NOT_FOUND",
                        "Visite introuvable."));
        requireOwner(visit, owner);
        requireStatus(visit, VisitStatus.REQUESTED, "VISIT_ALREADY_CONFIRMED");

        visit.setProofCode(generateProofCode());
        visit.setStatus(VisitStatus.CONFIRMED);
        return VisitResponse.from(visitRepository.save(visit), true);
    }

    /** Code à usage unique : il est effacé dès qu'il est accepté. */
    public VisitResponse prove(User tenant, Long visitId, VisitProofRequest request) {
        requireRole(tenant, Role.TENANT, "TENANT_ROLE_REQUIRED",
                "Seuls les étudiants peuvent valider une preuve de visite.");
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VISIT_NOT_FOUND",
                        "Visite introuvable."));
        if (visit.getTenant() == null || !Objects.equals(visit.getTenant().getId(), tenant.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "VISIT_NOT_FOR_TENANT",
                    "Cette visite n'appartient pas à cet étudiant.");
        }
        requireStatus(visit, VisitStatus.CONFIRMED, "VISIT_NOT_CONFIRMED");

        if (!constantTimeEquals(visit.getProofCode(), request.proofCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROOF_CODE",
                    "Code de preuve invalide.",
                    List.of(new ApiErrorResponse.FieldIssue("proofCode", "code invalide")));
        }

        visit.setStatus(VisitStatus.PROVEN);
        visit.setProvenAt(Instant.now());
        visit.setProofLatitude(request.latitude());
        visit.setProofLongitude(request.longitude());
        visit.setProofCode(null);
        return VisitResponse.from(visitRepository.save(visit), false);
    }

    private void requireOwner(Visit visit, User owner) {
        if (visit.getOwner() == null || !Objects.equals(visit.getOwner().getId(), owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "VISIT_NOT_OWNED",
                    "Vous n'êtes pas propriétaire de cette annonce.");
        }
    }

    private void requireStatus(Visit visit, VisitStatus expected, String code) {
        if (visit.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, code,
                    "Le statut de cette visite ne permet pas cette action.");
        }
    }

    private void requireRole(User user, Role role, String code, String message) {
        if (user == null || user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String generateProofCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }
}
