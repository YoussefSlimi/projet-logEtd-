package org.logetd.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.BookingRequest;
import org.logetd.dto.BookingResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration critique : la contrainte d'exclusion PostgreSQL doit produire un conflit 409,
 * jamais une erreur 500, lorsque deux réservations concurrentes portent sur les mêmes dates.
 */
@SpringBootTest
class BookingConcurrencyTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private UserRepository userRepository;

    private Long ownerId;
    private Long tenantId;
    private Long placeId;
    private Long listingId;
    private User owner;
    private User tenant;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(user("owner-" + suffix, Role.OWNER));
        tenant = userRepository.save(user("tenant-" + suffix, Role.TENANT));

        Listing listing = new Listing();
        listing.setOwner(owner);
        listing.setTitle("Studio concurrence " + suffix);
        listing.setDescription("Fixtures de test");
        listing.setCity("Monastir");
        listing.setGenderRule(GenderRule.MIXED);
        listing.setStatus(ListingStatus.PENDING);
        listingId = listingRepository.save(listing).getId();

        Place place = new Place();
        place.setListing(listing);
        place.setLabel("Chambre A");
        place.setMonthlyRentMillimes(350000L);
        place.setChargesMillimes(50000L);
        place.setEstimationsMillimes(0L);
        placeId = placeRepository.save(place).getId();

        ownerId = owner.getId();
        tenantId = tenant.getId();
        start = LocalDate.now().plusDays(20);
        end = start.plusDays(5);
    }

    @AfterEach
    void tearDown() {
        if (placeId != null) {
            bookingRepository.deleteAllById(bookingRepository.findByPlaceId(placeId).stream()
                    .map(Booking::getId).toList());
            placeRepository.deleteById(placeId);
        }
        if (listingId != null) {
            listingRepository.deleteById(listingId);
        }
        if (ownerId != null && tenantId != null) {
            userRepository.deleteAllById(List.of(tenantId, ownerId));
        }
    }

    @Test
    void twoConcurrentRequestsForSamePlaceYieldOneSuccessAndOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            Future<String> first = pool.submit(requestTask(gate));
            Future<String> second = pool.submit(requestTask(gate));
            gate.countDown();

            List<String> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertOneSuccessOneConflict(results);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoConcurrentAcceptsYieldOneSuccessAndOneConflict() throws Exception {
        // Deux demandes REQUESTED sont insérées directement : la contrainte SQL ne couvre que
        // les statuts actifs, c'est bien la transition vers ACCEPTED qui est critique.
        Booking firstRequest = bookingRepository.saveAndFlush(newBooking(BookingStatus.REQUESTED));
        Booking secondRequest = bookingRepository.saveAndFlush(newBooking(BookingStatus.REQUESTED));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            Future<String> first = pool.submit(acceptTask(gate, firstRequest.getId()));
            Future<String> second = pool.submit(acceptTask(gate, secondRequest.getId()));
            gate.countDown();

            List<String> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertOneSuccessOneConflict(results);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<String> requestTask(CountDownLatch gate) {
        return () -> {
            gate.await();
            try {
                BookingResponse response = bookingService.request(
                        tenant, new BookingRequest(placeId, start, end, 0L));
                return "OK:" + response.id();
            } catch (ApiException ex) {
                return ex.getCode();
            } catch (RuntimeException ex) {
                return "UNEXPECTED:" + ex.getClass().getSimpleName();
            }
        };
    }

    private Callable<String> acceptTask(CountDownLatch gate, Long bookingId) {
        return () -> {
            gate.await();
            try {
                BookingResponse response = bookingService.accept(owner, bookingId);
                return "OK:" + response.id();
            } catch (ApiException ex) {
                return ex.getCode();
            } catch (RuntimeException ex) {
                return "UNEXPECTED:" + ex.getClass().getSimpleName();
            }
        };
    }

    private void assertOneSuccessOneConflict(List<String> results) {
        long successes = results.stream().filter(result -> result.startsWith("OK:")).count();
        long conflicts = results.stream()
                .filter("BOOKING_DATES_UNAVAILABLE"::equals).count();
        assertEquals(1, successes, "Résultats : " + results);
        assertEquals(1, conflicts, "Résultats : " + results);
        assertTrue(results.stream().noneMatch(result -> result.startsWith("UNEXPECTED:")),
                "Aucune erreur inattendue (500) n'est tolérée : " + results);
    }

    private Booking newBooking(BookingStatus status) {
        Place place = placeRepository.findById(placeId).orElseThrow();
        Booking booking = new Booking();
        booking.setPlace(place);
        booking.setTenant(tenant);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setDepositMillimes(0L);
        booking.setStatus(status);
        return booking;
    }

    private User user(String prefix, Role role) {
        User user = new User();
        user.setPhone(prefix);
        user.setEmail(prefix + "@test.local");
        user.setPasswordHash("hash-de-test");
        user.setFullName("Utilisateur test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
