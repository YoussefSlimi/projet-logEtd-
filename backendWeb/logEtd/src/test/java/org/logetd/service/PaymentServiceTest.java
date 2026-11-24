package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.Place;
import org.logetd.domain.payment.Payment;
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.PaymentDeclaration;
import org.logetd.dto.PaymentPageResponse;
import org.logetd.dto.PaymentResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;
import org.logetd.shared.Role;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentProofStorageService proofStorage;

    @InjectMocks
    private PaymentService service;

    private final User tenant = user(1L, Role.TENANT);
    private final User owner = user(2L, Role.OWNER);
    private final User stranger = user(3L, Role.TENANT);
    private final MultipartFile proof = new MockMultipartFile(
            "proof", "recu.pdf", "application/pdf", "contenu".getBytes());

    @Test
    void declareCreatesDeclaredPaymentAndMovesBookingToDepositPending() {
        Booking booking = booking(BookingStatus.ACCEPTED);
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByDeclarationIdempotencyHash(anyString())).thenReturn(Optional.empty());
        when(proofStorage.store(proof)).thenReturn("proof.pdf");
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        PaymentResponse response = service.declare(tenant,
                new PaymentDeclaration(7L, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L),
                proof, "commande-1");

        assertEquals(9L, response.id());
        assertEquals(7L, response.bookingId());
        assertEquals(PaymentStatus.DECLARED, response.status());
        assertTrue(response.tenantDeclared());
        assertFalse(response.ownerConfirmed());
        assertNotNull(response.declaredAt());
        assertNull(response.confirmedAt());
        assertEquals("proof.pdf", response.proofPath());
        assertEquals(BookingStatus.DEPOSIT_PENDING, booking.getStatus());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertNotNull(captor.getValue().getDeclarationIdempotencyHash());
        assertEquals(64, captor.getValue().getDeclarationIdempotencyHash().length());
    }

    @Test
    void declareReplaysSameKeyWithoutSecondSaveOrSecondFile() {
        Booking booking = booking(BookingStatus.DEPOSIT_PENDING);
        Payment existing = declaredPayment();
        existing.setDeclarationIdempotencyHash("hash-connu");
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByDeclarationIdempotencyHash(anyString()))
                .thenReturn(Optional.of(existing));

        PaymentDeclaration request = new PaymentDeclaration(
                7L, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L);
        PaymentResponse first = service.declare(tenant, request, proof, "commande-1");
        PaymentResponse second = service.declare(tenant, request, proof, "commande-1");

        assertEquals(9L, first.id());
        assertEquals(first.id(), second.id());
        verify(bookingRepository, times(2)).findByIdForUpdate(7L);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(proofStorage);
    }

    @Test
    void declareRejectsOwnerRole() {
        ApiException ex = assertThrows(ApiException.class, () -> service.declare(owner,
                new PaymentDeclaration(7L, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L),
                proof, "commande-1"));

        assertEquals("TENANT_ROLE_REQUIRED", ex.getCode());
        assertEquals(403, ex.getStatus().value());
        verifyNoInteractions(bookingRepository, proofStorage);
    }

    @Test
    void declareRejectsAnotherTenantsBooking() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking(BookingStatus.ACCEPTED)));

        ApiException ex = assertThrows(ApiException.class, () -> service.declare(stranger,
                new PaymentDeclaration(7L, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L),
                proof, "commande-1"));

        assertEquals("PAYMENT_NOT_FOR_TENANT", ex.getCode());
        assertEquals(403, ex.getStatus().value());
        verifyNoInteractions(proofStorage);
    }

    @Test
    void declareRejectsBookingThatIsNotPayable() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking(BookingStatus.CANCELLED)));
        when(paymentRepository.findByDeclarationIdempotencyHash(anyString())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.declare(tenant,
                new PaymentDeclaration(7L, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L),
                proof, "commande-1"));

        assertEquals("BOOKING_NOT_PAYABLE", ex.getCode());
        assertEquals(409, ex.getStatus().value());
        verifyNoInteractions(proofStorage);
    }

    @Test
    void declareRejectsMissingIdempotencyKey() {
        ApiException ex = assertThrows(ApiException.class, () -> service.declare(tenant,
                new PaymentDeclaration(7L, PaymentType.DEPOSIT, PaymentMethod.D17, 50000L),
                proof, null));

        assertEquals("IDEMPOTENCY_KEY_MISSING", ex.getCode());
        assertEquals(400, ex.getStatus().value());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void declareRejectsNonPositiveAmount() {
        ApiException ex = assertThrows(ApiException.class, () -> service.declare(tenant,
                new PaymentDeclaration(7L, PaymentType.DEPOSIT, PaymentMethod.D17, 0L),
                proof, "commande-1"));

        assertEquals("INVALID_PAYMENT_AMOUNT", ex.getCode());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void confirmSetsBothFlagsConfirmsPaymentAndBooking() {
        Payment payment = declaredPayment();
        Booking booking = payment.getBooking();
        booking.setStatus(BookingStatus.DEPOSIT_PENDING);
        when(paymentRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        PaymentResponse response = service.confirm(owner, 9L, "confirmation-1");

        assertEquals(PaymentStatus.CONFIRMED, response.status());
        assertTrue(response.tenantDeclared());
        assertTrue(response.ownerConfirmed());
        assertNotNull(response.confirmedAt());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(bookingRepository).saveAndFlush(booking);
    }

    @Test
    void confirmWithoutTenantDeclarationKeepsStatusPending() {
        Payment payment = new Payment();
        payment.setId(9L);
        payment.setBooking(booking(BookingStatus.DEPOSIT_PENDING));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTenantDeclared(false);
        when(paymentRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(payment));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirm(owner, 9L, "confirmation-1"));

        assertEquals("PAYMENT_NOT_DECLARED", ex.getCode());
        assertEquals(409, ex.getStatus().value());
        assertFalse(payment.isOwnerConfirmed());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(BookingStatus.DEPOSIT_PENDING, payment.getBooking().getStatus());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmRejectsAnotherOwner() {
        Payment payment = declaredPayment();
        when(paymentRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(payment));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirm(user(99L, Role.OWNER), 9L, "confirmation-1"));

        assertEquals("PAYMENT_NOT_OWNED", ex.getCode());
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void confirmReplaysSameKeyWithoutSecondTransition() {
        Payment payment = declaredPayment();
        payment.getBooking().setStatus(BookingStatus.DEPOSIT_PENDING);
        when(paymentRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        PaymentResponse first = service.confirm(owner, 9L, "confirmation-1");
        PaymentResponse second = service.confirm(owner, 9L, "confirmation-1");

        assertEquals(first.id(), second.id());
        assertEquals(PaymentStatus.CONFIRMED, second.status());
        verify(paymentRepository, times(1)).saveAndFlush(payment);
        verify(bookingRepository, times(1)).saveAndFlush(payment.getBooking());
    }

    @Test
    void confirmRejectsReuseOfKeyForAnotherCall() {
        Payment payment = declaredPayment();
        payment.setConfirmationIdempotencyHash("autre-hash");
        when(paymentRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(payment));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirm(owner, 9L, "confirmation-1"));

        assertEquals("IDEMPOTENCY_KEY_REUSE", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void findByBookingRejectsStranger() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.findByBooking(stranger, 7L, 0, 20));

        assertEquals("PAYMENT_NOT_FOR_BOOKING", ex.getCode());
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void findByBookingReturnsPagedPaymentDtos() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        Payment payment = declaredPayment();
        payment.setBooking(booking);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingIdOrderByIdAsc(eq(7L), any())).thenReturn(
                new PageImpl<>(List.of(payment)));

        PaymentPageResponse response = service.findByBooking(tenant, 7L, 0, 20);

        assertEquals(1, response.content().size());
        assertEquals(9L, response.content().get(0).id());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
    }

    @Test
    void findByBookingRejectsOversizedPage() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.findByBooking(tenant, 7L, 0, 100));

        assertEquals("PAGE_SIZE_TOO_LARGE", ex.getCode());
        verifyNoInteractions(paymentRepository);
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Booking booking(BookingStatus status) {
        Listing listing = new Listing();
        listing.setId(10L);
        listing.setStatus(ListingStatus.PENDING);
        listing.setGenderRule(GenderRule.MIXED);
        listing.setOwner(owner);

        Place place = new Place();
        place.setId(42L);
        place.setListing(listing);

        Booking booking = new Booking();
        booking.setId(7L);
        booking.setPlace(place);
        booking.setTenant(tenant);
        booking.setStatus(status);
        booking.setDepositMillimes(50000L);
        return booking;
    }

    private Payment declaredPayment() {
        Payment payment = new Payment();
        payment.setId(9L);
        payment.setBooking(booking(BookingStatus.DEPOSIT_PENDING));
        payment.setPaymentType(PaymentType.DEPOSIT);
        payment.setPaymentMethod(PaymentMethod.D17);
        payment.setAmountMillimes(50000L);
        payment.setStatus(PaymentStatus.DECLARED);
        payment.setTenantDeclared(true);
        payment.setDeclaredAt(Instant.now());
        payment.setProofPath("proof.pdf");
        return payment;
    }
}
