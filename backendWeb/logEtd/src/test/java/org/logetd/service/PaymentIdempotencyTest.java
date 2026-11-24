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
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.PaymentDeclaration;
import org.logetd.dto.PaymentResponse;
import org.logetd.service.PaymentProofStorageService;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test d'acceptation de l'Étape 6 : deux appels avec la même Idempotency-Key ne créent
 * qu'un seul paiement, y compris lorsqu'ils sont concurrents (verrou sur la réservation).
 */
@SpringBootTest
class PaymentIdempotencyTest {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PaymentProofStorageService proofStorage;

    private User owner;
    private User tenant;
    private Long bookingId;
    private Long placeId;
    private Long listingId;
    private Long ownerId;
    private Long tenantId;
    private MultipartFile proof;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(user("owner-" + suffix, Role.OWNER));
        tenant = userRepository.save(user("tenant-" + suffix, Role.TENANT));
        ownerId = owner.getId();
        tenantId = tenant.getId();

        Listing listing = new Listing();
        listing.setOwner(owner);
        listing.setTitle("Studio paiement " + suffix);
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

        Booking booking = new Booking();
        booking.setPlace(place);
        booking.setTenant(tenant);
        booking.setStartDate(LocalDate.now().plusDays(20));
        booking.setEndDate(LocalDate.now().plusDays(25));
        booking.setDepositMillimes(50000L);
        booking.setStatus(BookingStatus.ACCEPTED);
        bookingId = bookingRepository.saveAndFlush(booking).getId();

        proof = new MockMultipartFile("proof", "recu.pdf", "application/pdf", "contenu".getBytes());
        when(proofStorage.store(any(MultipartFile.class))).thenReturn("proof.pdf");
    }

    @AfterEach
    void tearDown() {
        if (bookingId != null) {
            paymentRepository.deleteAllById(
                    paymentRepository.findByBookingIdOrderByIdAsc(bookingId, PageRequest.of(0, 50))
                            .getContent().stream().map(p -> p.getId()).toList());
            bookingRepository.deleteAllById(List.of(bookingId));
        }
        if (placeId != null) {
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
    void twoSequentialDeclarationsWithSameKeyCreateOnlyOnePayment() {
        PaymentDeclaration request = request();

        PaymentResponse first = paymentService.declare(tenant, request, proof, "cle-1");
        PaymentResponse second = paymentService.declare(tenant, request, proof, "cle-1");

        assertEquals(first.id(), second.id());
        assertEquals(PaymentStatus.DECLARED, second.status());
        assertEquals(1, paymentCount());
        assertEquals(BookingStatus.DEPOSIT_PENDING,
                bookingRepository.findById(bookingId).orElseThrow().getStatus());
    }

    @Test
    void twoConcurrentDeclarationsWithSameKeyCreateOnlyOnePayment() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            Callable<Long> task = () -> {
                gate.await();
                return paymentService.declare(tenant, request(), proof, "cle-concurrente").id();
            };
            Future<Long> first = pool.submit(task);
            Future<Long> second = pool.submit(task);
            gate.countDown();

            Long firstId = first.get(30, TimeUnit.SECONDS);
            Long secondId = second.get(30, TimeUnit.SECONDS);
            assertEquals(firstId, secondId);
            assertEquals(1, paymentCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void confirmReplayWithSameKeyDoesNotDuplicateEffects() {
        PaymentResponse declared = paymentService.declare(tenant, request(), proof, "cle-1");

        PaymentResponse first = paymentService.confirm(owner, declared.id(), "confirmation-1");
        PaymentResponse second = paymentService.confirm(owner, declared.id(), "confirmation-1");

        assertEquals(first.id(), second.id());
        assertEquals(PaymentStatus.CONFIRMED, second.status());
        assertTrue(second.ownerConfirmed());
        assertEquals(1, paymentCount());
        assertEquals(BookingStatus.CONFIRMED,
                bookingRepository.findById(bookingId).orElseThrow().getStatus());
    }

    private PaymentDeclaration request() {
        return new PaymentDeclaration(bookingId, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L);
    }

    private long paymentCount() {
        return paymentRepository.findByBookingIdOrderByIdAsc(bookingId, PageRequest.of(0, 20))
                .getTotalElements();
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
