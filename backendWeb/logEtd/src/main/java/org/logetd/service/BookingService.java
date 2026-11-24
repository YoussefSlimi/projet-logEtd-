package org.logetd.service;

import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.BookingRequest;
import org.logetd.dto.BookingResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.Role;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class BookingService {

    /** Statuts qui bloquent une place : demande en attente + statuts couverts par la contrainte SQL. */
    public static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.DEPOSIT_PENDING,
            BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    private final BookingRepository bookingRepository;
    private final PlaceRepository placeRepository;

    public BookingService(BookingRepository bookingRepository, PlaceRepository placeRepository) {
        this.bookingRepository = bookingRepository;
        this.placeRepository = placeRepository;
    }

    public BookingResponse request(User tenant, BookingRequest request) {
        requireRole(tenant, Role.TENANT, "TENANT_ROLE_REQUIRED",
                "Seuls les étudiants peuvent réserver.");
        validatePeriod(request);

        // Le verrou sur la place sérialise deux demandes concurrentes : la seconde voit la
        // réservation REQUESTED de la première et est rejetée avec un 409.
        Place place = placeRepository.findByIdForUpdate(request.placeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND",
                        "Place introuvable."));
        if (bookingRepository.existsOverlapping(place.getId(), request.startDate(), request.endDate(),
                BLOCKING_STATUSES)) {
            throw datesUnavailable();
        }

        Booking booking = new Booking();
        booking.setPlace(place);
        booking.setTenant(tenant);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setDepositMillimes(request.depositMillimes() == null ? 0L : request.depositMillimes());
        booking.setStatus(BookingStatus.REQUESTED);
        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    public BookingResponse accept(User owner, Long bookingId) {
        return transition(owner, bookingId, BookingStatus.REQUESTED, BookingStatus.ACCEPTED);
    }

    public BookingResponse markDepositPending(User owner, Long bookingId) {
        return transition(owner, bookingId, BookingStatus.ACCEPTED, BookingStatus.DEPOSIT_PENDING);
    }

    public BookingResponse confirmDeposit(User owner, Long bookingId) {
        return transition(owner, bookingId, BookingStatus.DEPOSIT_PENDING, BookingStatus.CONFIRMED);
    }

    private BookingResponse transition(User owner, Long bookingId,
                                       BookingStatus expected, BookingStatus next) {
        requireRole(owner, Role.OWNER, "OWNER_ROLE_REQUIRED",
                "Seuls les propriétaires peuvent gérer les réservations.");
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "Réservation introuvable."));
        requireOwner(booking, owner);
        if (booking.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_STATUS_INVALID",
                    "Le statut de cette réservation ne permet pas cette action.",
                    List.of(new ApiErrorResponse.FieldIssue("status", "attendu : " + expected.name())));
        }

        booking.setStatus(next);
        try {
            // Le flush déclenche la contrainte PostgreSQL no_overlap_active_booking.
            return BookingResponse.from(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException | CannotAcquireLockException ex) {
            // Violation de la contrainte d'exclusion OU deadlock entre deux validations concurrentes.
            throw datesUnavailable();
        }
    }

    private void validatePeriod(BookingRequest request) {
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_PERIOD",
                    "La date de fin doit suivre ou égaler la date de début.",
                    List.of(new ApiErrorResponse.FieldIssue("endDate", ">= startDate")));
        }
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_PERIOD",
                    "La réservation ne peut pas démarrer dans le passé.",
                    List.of(new ApiErrorResponse.FieldIssue("startDate", ">= aujourd'hui")));
        }
    }

    private void requireOwner(Booking booking, User owner) {
        if (booking.getPlace() == null || booking.getPlace().getListing() == null
                || booking.getPlace().getListing().getOwner() == null
                || !Objects.equals(booking.getPlace().getListing().getOwner().getId(), owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BOOKING_NOT_OWNED",
                    "Vous n'êtes pas propriétaire de cette place.");
        }
    }

    private void requireRole(User user, Role role, String code, String message) {
        if (user == null || user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private ApiException datesUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "BOOKING_DATES_UNAVAILABLE",
                "Ces dates ne sont plus disponibles.",
                List.of(new ApiErrorResponse.FieldIssue("startDate", "overlap")));
    }
}
