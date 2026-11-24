package org.logetd.service;

import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.payment.Payment;
import org.logetd.domain.payment.PaymentRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.PaymentDeclaration;
import org.logetd.dto.PaymentPageResponse;
import org.logetd.dto.PaymentResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class PaymentService {

    /** Statuts d'une réservation qui attend un versement. */
    private static final List<BookingStatus> PAYABLE_STATUSES = List.of(
            BookingStatus.ACCEPTED, BookingStatus.DEPOSIT_PENDING);
    private static final int MAX_PAGE_SIZE = 50;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentProofStorageService proofStorage;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          PaymentProofStorageService proofStorage) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.proofStorage = proofStorage;
    }

    /**
     * L'étudiant déclare un versement et joint sa preuve. La clé d'idempotence est liée à la
     * réservation : deux appels avec la même clé sur la même réservation renvoient le même paiement.
     */
    public PaymentResponse declare(User tenant, PaymentDeclaration request,
                                   MultipartFile proof, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        requireRole(tenant, Role.TENANT, "TENANT_ROLE_REQUIRED",
                "Seuls les étudiants peuvent déclarer un paiement.");
        if (request == null || request.bookingId() == null) {
            throw badRequest("INVALID_PAYMENT", "Identifiant de réservation obligatoire.", "bookingId");
        }
        if (request.paymentType() == null || request.paymentMethod() == null) {
            throw badRequest("INVALID_PAYMENT", "Type et moyen de paiement obligatoires.", "paymentType");
        }
        if (request.amountMillimes() == null || request.amountMillimes() <= 0) {
            throw badRequest("INVALID_PAYMENT_AMOUNT", "Le montant doit être strictement positif.",
                    "amountMillimes");
        }

        String hash = idempotencyHash(key, "declaration", request.bookingId());
        // Le verrou sur la réservation sérialise deux déclarations concurrentes, y compris
        // avec la même clé : le second appel revérifie la clé après avoir acquis le verrou.
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "Réservation introuvable."));
        Optional<Payment> replay = paymentRepository.findByDeclarationIdempotencyHash(hash);
        if (replay.isPresent()) {
            return PaymentResponse.from(replay.get());
        }
        if (booking.getTenant() == null || !Objects.equals(booking.getTenant().getId(), tenant.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_NOT_FOR_TENANT",
                    "Cette réservation n'appartient pas à cet étudiant.");
        }
        if (!PAYABLE_STATUSES.contains(booking.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_PAYABLE",
                    "Cette réservation n'attend pas de paiement.");
        }

        String proofPath = proofStorage.store(proof);
        try {
            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setPaymentType(request.paymentType());
            payment.setPaymentMethod(request.paymentMethod());
            payment.setAmountMillimes(request.amountMillimes());
            payment.setStatus(PaymentStatus.DECLARED);
            payment.setTenantDeclared(true);
            payment.setOwnerConfirmed(false);
            payment.setDeclaredAt(Instant.now());
            payment.setProofPath(proofPath);
            payment.setDeclarationIdempotencyHash(hash);

            Payment saved = paymentRepository.saveAndFlush(payment);
            if (booking.getStatus() == BookingStatus.ACCEPTED) {
                booking.setStatus(BookingStatus.DEPOSIT_PENDING);
                bookingRepository.saveAndFlush(booking);
            }
            return PaymentResponse.from(saved);
        } catch (RuntimeException ex) {
            proofStorage.delete(proofPath);
            throw ex;
        }
    }

    /**
     * Le propriétaire confirme réception. Le statut ne passe en CONFIRMED que si l'étudiant
     * a déclaré le versement ET si le propriétaire l'a confirmé (double validation).
     */
    public PaymentResponse confirm(User owner, Long paymentId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        requireRole(owner, Role.OWNER, "OWNER_ROLE_REQUIRED",
                "Seuls les propriétaires peuvent confirmer un paiement.");
        String hash = idempotencyHash(key, "confirmation", paymentId);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                        "Paiement introuvable."));
        // Même clé, même paiement : renvoi de la réponse déjà calculée, sans nouvel effet.
        if (hash.equals(payment.getConfirmationIdempotencyHash())) {
            return PaymentResponse.from(payment);
        }
        if (payment.getConfirmationIdempotencyHash() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE",
                    "Cette clé d'idempotence a déjà été utilisée pour un autre appel.");
        }
        requirePaymentOwner(payment, owner);
        if (payment.getStatus() == PaymentStatus.CONFIRMED || payment.getStatus() == PaymentStatus.BLOCKED) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_STATUS_INVALID",
                    "Le statut de ce paiement ne permet pas cette action.");
        }
        if (!payment.isTenantDeclared()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_DECLARED",
                    "L'étudiant n'a pas encore déclaré ce versement.");
        }

        payment.setOwnerConfirmed(true);
        payment.setConfirmedAt(Instant.now());
        payment.setStatus(deriveStatus(payment));
        payment.setConfirmationIdempotencyHash(hash);
        Payment saved = paymentRepository.saveAndFlush(payment);

        Booking booking = saved.getBooking();
        if (booking != null && booking.getStatus() == BookingStatus.DEPOSIT_PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.saveAndFlush(booking);
        }
        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaymentPageResponse findByBooking(User viewer, Long bookingId, int page, int size) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "Réservation introuvable."));
        if (!canView(booking, viewer)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_NOT_FOR_BOOKING",
                    "Vous n'êtes pas partie prenante de cette réservation.");
        }
        if (page < 0) {
            throw badRequest("INVALID_PAGE", "La page doit être positive ou nulle.", "page");
        }
        if (size < 1) {
            throw badRequest("INVALID_PAGE_SIZE", "La taille de page doit être au moins 1.", "size");
        }
        if (size > MAX_PAGE_SIZE) {
            throw badRequest("PAGE_SIZE_TOO_LARGE", "La taille de page ne peut pas dépasser 50.", "size");
        }

        Page<Payment> result = paymentRepository.findByBookingIdOrderByIdAsc(
                bookingId, PageRequest.of(page, size));
        return new PaymentPageResponse(
                result.getContent().stream().map(PaymentResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private PaymentStatus deriveStatus(Payment payment) {
        if (payment.isTenantDeclared() && payment.isOwnerConfirmed()) {
            return PaymentStatus.CONFIRMED;
        }
        return payment.isTenantDeclared() ? PaymentStatus.DECLARED : PaymentStatus.PENDING;
    }

    private boolean canView(Booking booking, User viewer) {
        if (viewer == null) {
            return false;
        }
        if (viewer.getRole() == Role.ADMIN) {
            return true;
        }
        if (booking.getTenant() != null && Objects.equals(booking.getTenant().getId(), viewer.getId())) {
            return true;
        }
        return booking.getPlace() != null && booking.getPlace().getListing() != null
                && booking.getPlace().getListing().getOwner() != null
                && Objects.equals(booking.getPlace().getListing().getOwner().getId(), viewer.getId());
    }

    private void requirePaymentOwner(Payment payment, User owner) {
        Booking booking = payment.getBooking();
        if (booking == null || booking.getPlace() == null || booking.getPlace().getListing() == null
                || booking.getPlace().getListing().getOwner() == null
                || !Objects.equals(booking.getPlace().getListing().getOwner().getId(), owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_NOT_OWNED",
                    "Vous n'êtes pas propriétaire de cette réservation.");
        }
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw badRequest("IDEMPOTENCY_KEY_MISSING", "L'en-tête Idempotency-Key est obligatoire.",
                    "Idempotency-Key");
        }
        String trimmed = key.trim();
        if (trimmed.length() > 255) {
            throw badRequest("IDEMPOTENCY_KEY_INVALID", "L'en-tête Idempotency-Key est trop long.",
                    "Idempotency-Key");
        }
        return trimmed;
    }

    private String idempotencyHash(String key, String scope, Long targetId) {
        try {
            byte[] input = (scope + "|" + targetId + "|" + key).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }

    private void requireRole(User user, Role role, String code, String message) {
        if (user == null || user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private ApiException badRequest(String code, String message, String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new ApiErrorResponse.FieldIssue(field, "valeur invalide")));
    }
}
