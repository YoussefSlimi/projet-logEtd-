package org.logetd.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.MessageRequest;
import org.logetd.dto.MessageResponse;
import org.logetd.security.JwtService;
import org.logetd.service.MessagingService;
import org.logetd.service.MessageBroadcaster;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingWebSocketHandlerTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final JwtService jwtService = new JwtService(SECRET, 900, 604800);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserRepository userRepository;
    @Mock
    private MessagingService messagingService;
    @Mock
    private MessageBroadcaster broadcaster;
    @Mock
    private WebSocketSession session;

    @Test
    void missingTokenIsRejectedAtHandshake() throws Exception {
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/messages"));
        when(session.isOpen()).thenReturn(true);
        MessagingWebSocketHandler handler = handler();

        handler.afterConnectionEstablished(session);

        assertSentPayloadContains("UNAUTHENTICATED");
        verify(session).close(any(CloseStatus.class));
        verify(messagingService, never()).send(any(), any());
    }

    @Test
    void invalidTokenIsRejectedAtHandshake() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.isOpen()).thenReturn(true);
        MessagingWebSocketHandler handler = handler();

        handler.afterConnectionEstablished(session);

        assertSentPayloadContains("INVALID_TOKEN");
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void subscribeRegistersSessionForBooking() throws Exception {
        authenticateWithQueryToken();
        MessagingWebSocketHandler handler = handler();
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"SUBSCRIBE\",\"bookingId\":7}"));

        verify(messagingService).requireBookingParticipant(any(), org.mockito.ArgumentMatchers.eq(7L));
        verify(broadcaster).subscribe(7L, session);
        assertSentPayloadContains("SUBSCRIBED");
    }

    @Test
    void messageFrameUsesSameServiceAsRestFallback() throws Exception {
        authenticateWithHeaderToken();
        MessagingWebSocketHandler handler = handler();
        when(session.getId()).thenReturn("s1");
        when(messagingService.send(any(), any())).thenReturn(new MessageResponse(
                11L, 7L, 1L, "Bonjour", Instant.now(), null));

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage(
                "{\"type\":\"MESSAGE\",\"bookingId\":7,\"content\":\"Bonjour\"}"));

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(messagingService).send(any(), request.capture());
        org.junit.jupiter.api.Assertions.assertEquals(7L, request.getValue().bookingId());
        org.junit.jupiter.api.Assertions.assertEquals("Bonjour", request.getValue().content());
    }

    @Test
    void unauthenticatedSessionCannotSendMessages() throws Exception {
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/messages"));
        when(session.getId()).thenReturn("s1");
        MessagingWebSocketHandler handler = handler();

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage(
                "{\"type\":\"MESSAGE\",\"bookingId\":7,\"content\":\"Bonjour\"}"));

        verify(messagingService, never()).send(any(), any());
    }

    private MessagingWebSocketHandler handler() {
        return new MessagingWebSocketHandler(jwtService, userRepository,
                messagingService, broadcaster, objectMapper);
    }

    private void authenticateWithQueryToken() {
        String token = jwtService.generateAccessToken(1L, Role.TENANT);
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.getUri()).thenReturn(
                URI.create("ws://localhost/ws/messages?token=" + token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser(1L)));
    }

    private void authenticateWithHeaderToken() {
        String token = jwtService.generateAccessToken(1L, Role.TENANT);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser(1L)));
    }

    private User activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.TENANT);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private void assertSentPayloadContains(String fragment) throws Exception {
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(sent.capture());
        boolean found = sent.getAllValues().stream()
                .anyMatch(message -> message.getPayload().contains(fragment));
        assertTrue(found, "Trame attendue contenant " + fragment);
    }
}
