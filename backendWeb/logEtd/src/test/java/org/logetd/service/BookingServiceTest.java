package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.BookingRequest;
import org.logetd.dto.BookingResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PlaceRepository placeRepository;

    @InjectMocks
    private BookingService service;

    private final User tenant = user(1L, Role.TENANT);
    private final User owner = user(2L, Role.OWNER);
    private final LocalDate start = LocalDate.now().plusDays(10);
    private final LocalDate end = start.plusDays(5);

    @Test
    void requestCreatesBookingInRequestedStatus() {
        when(placeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(place()));
        when(bookingRepository.existsOverlapping(eq(42L), eq(start), eq(end), anyCollection()))
                .thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        BookingResponse response = service.request(tenant, new BookingRequest(42L, start, end, 50000L));

        assertEquals(7L, response.id());
        assertEquals(BookingStatus.REQUESTED, response.status());
        assertEquals(42L, response.placeId());
        assertEquals(10L, response.listingId());
        assertEquals(50000L, response.depositMillimes());
    }

    @Test
    void requestDefaultsDepositToZero() {
        when(placeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(place()));
        when(bookingRepository.existsOverlapping(eq(42L), eq(start), eq(end), anyCollection()))
                .thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(0L, service.request(tenant, new BookingRequest(42L, start, end, null)).depositMillimes());
    }

    @Test
    void requestRejectsOwnerRole() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(owner, new BookingRequest(42L, start, end, null)));

        assertEquals("TENANT_ROLE_REQUIRED", ex.getCode());
        verifyNoInteractions(placeRepository);
    }

    @Test
    void requestRejectsInvertedPeriod() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(tenant, new BookingRequest(42L, end, start, null)));

        assertEquals("INVALID_BOOKING_PERIOD", ex.getCode());
        verifyNoInteractions(placeRepository);
    }

    @Test
    void requestRejectsPastDate() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(tenant, new BookingRequest(42L, LocalDate.now().minusDays(1), end, null)));

        assertEquals("INVALID_BOOKING_PERIOD", ex.getCode());
    }

    @Test
    void requestRejectsUnknownPlace() {
        when(placeRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(tenant, new BookingRequest(42L, start, end, null)));

        assertEquals("PLACE_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void requestRejectsOverlappingDatesWith409() {
        when(placeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(place()));
        when(bookingRepository.existsOverlapping(eq(42L), eq(start), eq(end), anyCollection()))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(tenant, new BookingRequest(42L, start, end, null)));

        assertEquals(409, ex.getStatus().value());
        assertEquals("BOOKING_DATES_UNAVAILABLE", ex.getCode());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptMovesStatusToAccepted() {
        Booking booking = booking(BookingStatus.REQUESTED);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);

        assertEquals(BookingStatus.ACCEPTED, service.accept(owner, 7L).status());
    }

    @Test
    void acceptRejectsAnotherOwner() {
        Booking booking = booking(BookingStatus.REQUESTED);
        booking.getPlace().getListing().setOwner(user(99L, Role.OWNER));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        ApiException ex = assertThrows(ApiException.class, () -> service.accept(owner, 7L));

        assertEquals("BOOKING_NOT_OWNED", ex.getCode());
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void acceptRejectsInvalidStatus() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        ApiException ex = assertThrows(ApiException.class, () -> service.accept(owner, 7L));

        assertEquals("BOOKING_STATUS_INVALID", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void acceptTranslatesDatabaseConstraintViolationTo409() {
        Booking booking = booking(BookingStatus.REQUESTED);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(booking)).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("ERROR: conflict with constraint \"no_overlap_active_booking\"")));

        ApiException ex = assertThrows(ApiException.class, () -> service.accept(owner, 7L));

        assertEquals("BOOKING_DATES_UNAVAILABLE", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void fullStatusChainIsRespected() {
        Booking booking = booking(BookingStatus.REQUESTED);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);

        assertEquals(BookingStatus.ACCEPTED, service.accept(owner, 7L).status());
        assertEquals(BookingStatus.DEPOSIT_PENDING, service.markDepositPending(owner, 7L).status());
        assertEquals(BookingStatus.CONFIRMED, service.confirmDeposit(owner, 7L).status());
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Place place() {
        Listing listing = new Listing();
        listing.setId(10L);
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setOwner(owner);

        Place place = new Place();
        place.setId(42L);
        place.setListing(listing);
        return place;
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setPlace(place());
        booking.setTenant(tenant);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setStatus(status);
        return booking;
    }
}
