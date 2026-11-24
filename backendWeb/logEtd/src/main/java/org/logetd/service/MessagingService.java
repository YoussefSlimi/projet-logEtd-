package org.logetd.service;

import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.messaging.Message;
import org.logetd.domain.messaging.MessageRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.MessagePageResponse;
import org.logetd.dto.MessageRequest;
import org.logetd.dto.MessageResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.AuditAction;
import org.logetd.shared.Audited;
import org.logetd.shared.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Messagerie liée à une réservation, partagée entre le WebSocket et le repli REST. */
@Service
@Transactional
public class MessagingService {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_PAGE_SIZE = 50;

    private final MessageRepository messageRepository;
    private final BookingRepository bookingRepository;
    private final PhoneMaskingService maskingService;
    private final MessageBroadcaster broadcaster;

    public MessagingService(MessageRepository messageRepository,
                            BookingRepository bookingRepository,
                            PhoneMaskingService maskingService,
                            MessageBroadcaster broadcaster) {
        this.messageRepository = messageRepository;
        this.bookingRepository = bookingRepository;
        this.maskingService = maskingService;
        this.broadcaster = broadcaster;
    }

    @Audited(action = AuditAction.MESSAGE_SENT, targetType = "Message", targetId = "#result.id()")
    public MessageResponse send(User sender, MessageRequest request) {
        if (request == null || request.bookingId() == null) {
            throw badRequest("INVALID_MESSAGE", "Identifiant de réservation obligatoire.", "bookingId");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw badRequest("INVALID_MESSAGE", "Le contenu du message est obligatoire.", "content");
        }
        if (request.content().length() > MAX_CONTENT_LENGTH) {
            throw badRequest("INVALID_MESSAGE", "Le message ne doit pas dépasser 2000 caractères.", "content");
        }

        Booking booking = booking(request.bookingId());
        requireParticipant(sender, booking);

        Message message = new Message();
        message.setBooking(booking);
        message.setSender(sender);
        message.setContent(maskingService.mask(request.content()));

        MessageResponse response = MessageResponse.from(messageRepository.save(message));
        broadcaster.broadcast(booking.getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public MessagePageResponse findByBooking(User viewer, Long bookingId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("INVALID_PAGE", "Page positive ou nulle et taille entre 1 et 50.", "page/size");
        }
        Booking booking = booking(bookingId);
        requireParticipant(viewer, booking);
        Page<Message> messages = messageRepository.findByBookingIdOrderBySentAtAsc(
                bookingId, PageRequest.of(page, size, Sort.by("sentAt").ascending().and(Sort.by("id"))));
        List<MessageResponse> content = messages.getContent().stream().map(MessageResponse::from).toList();
        return new MessagePageResponse(content, page, size,
                messages.getTotalElements(), messages.getTotalPages());
    }

    /** Utilisé par le WebSocket pour valider un abonnement avant d'enregistrer la session. */
    @Transactional(readOnly = true)
    public void requireBookingParticipant(User user, Long bookingId) {
        requireParticipant(user, booking(bookingId));
    }

    private Booking booking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "Réservation introuvable."));
    }

    private void requireParticipant(User user, Booking booking) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
        }
        boolean participant = user.getRole() == Role.ADMIN
                || Objects.equals(user.getId(), booking.getTenant().getId())
                || (booking.getPlace().getListing() != null
                && Objects.equals(user.getId(), booking.getPlace().getListing().getOwner().getId()));
        if (!participant) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MESSAGE_NOT_FOR_BOOKING",
                    "Vous n'êtes pas partie prenante de cette réservation.");
        }
    }

    private ApiException badRequest(String code, String message, String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new ApiErrorResponse.FieldIssue(field, "valeur invalide")));
    }
}
