package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.booking.Booking;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.Place;
import org.logetd.domain.messaging.Message;
import org.logetd.domain.messaging.MessageRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.MessagePageResponse;
import org.logetd.dto.MessageRequest;
import org.logetd.dto.MessageResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.Role;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Spy
    private PhoneMaskingService maskingService = new PhoneMaskingService();
    @Mock
    private MessageBroadcaster broadcaster;

    @InjectMocks
    private MessagingService service;

    private final Booking booking = booking();

    @Test
    void tenantSendsMessageWithMaskedPhoneAndBroadcasts() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setSentAt(Instant.parse("2026-11-24T12:00:00Z"));
            return saved;
        });

        MessageResponse response = service.send(user(1L, Role.TENANT),
                new MessageRequest(7L, "Appelez +216 12 345 678"));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertEquals("Appelez +216******", saved.getValue().getContent());
        assertEquals(11L, response.id());
        assertEquals("Appelez +216******", response.content());
        verify(broadcaster).broadcast(7L, response);
    }

    @Test
    void ownerOfListingCanSendMessage() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        MessageResponse response = service.send(user(2L, Role.OWNER),
                new MessageRequest(7L, "Bonjour"));

        assertEquals(2L, response.senderId());
    }

    @Test
    void outsiderCannotSendMessage() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        ApiException ex = assertThrows(ApiException.class, () -> service.send(user(3L, Role.TENANT),
                new MessageRequest(7L, "Bonjour")));

        assertEquals("MESSAGE_NOT_FOR_BOOKING", ex.getCode());
        assertEquals(403, ex.getStatus().value());
        verify(messageRepository, never()).save(any());
        verify(broadcaster, never()).broadcast(any(), any());
    }

    @Test
    void unknownBookingReturns404() {
        when(bookingRepository.findById(8L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.send(user(1L, Role.TENANT),
                new MessageRequest(8L, "Bonjour")));

        assertEquals("BOOKING_NOT_FOUND", ex.getCode());
    }

    @Test
    void blankContentIsRejectedBeforeDatabaseAccess() {
        ApiException ex = assertThrows(ApiException.class, () -> service.send(user(1L, Role.TENANT),
                new MessageRequest(7L, "   ")));

        assertEquals("INVALID_MESSAGE", ex.getCode());
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    void historyIsPaginatedForParticipant() {
        Message message = new Message();
        message.setId(11L);
        message.setBooking(booking);
        message.setSender(booking.getTenant());
        message.setContent("Bonjour");
        message.setSentAt(Instant.parse("2026-11-24T12:00:00Z"));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(messageRepository.findByBookingIdOrderBySentAtAsc(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));

        MessagePageResponse page = service.findByBooking(user(1L, Role.TENANT), 7L, 0, 20);

        assertEquals(1, page.content().size());
        assertEquals(11L, page.content().get(0).id());
        assertEquals(1, page.totalElements());
    }

    private Booking booking() {
        User owner = user(2L, Role.OWNER);
        Listing listing = new Listing();
        listing.setOwner(owner);
        Place place = new Place();
        place.setListing(listing);
        Booking result = new Booking();
        result.setId(7L);
        result.setPlace(place);
        result.setTenant(user(1L, Role.TENANT));
        return result;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
