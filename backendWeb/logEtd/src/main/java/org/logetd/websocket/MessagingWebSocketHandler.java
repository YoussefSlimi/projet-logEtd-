package org.logetd.websocket;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.MessageRequest;
import org.logetd.dto.WebSocketAck;
import org.logetd.dto.WebSocketFrame;
import org.logetd.security.JwtService;
import org.logetd.service.MessagingService;
import org.logetd.service.MessageBroadcaster;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.UserStatus;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket brut : le JWT est fourni au handshake (query {@code token} ou en-tête Authorization),
 * puis vérifié ici avant toute lecture/écriture. Une trame {@code SUBSCRIBE} abonne la session
 * à une réservation, une trame {@code MESSAGE} utilise le même service que le repli REST.
 */
@Component
public class MessagingWebSocketHandler extends TextWebSocketHandler {

    public static final int MAX_FRAME_LENGTH = 8192;

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final MessagingService messagingService;
    private final MessageBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final Map<String, User> authenticatedUsers = new ConcurrentHashMap<>();

    public MessagingWebSocketHandler(JwtService jwtService,
                                     UserRepository userRepository,
                                     MessagingService messagingService,
                                     MessageBroadcaster broadcaster,
                                     ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.messagingService = messagingService;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null) {
            reject(session, "UNAUTHENTICATED", "Token JWT manquant.");
            return;
        }
        try {
            Jws<Claims> claims = jwtService.parse(token);
            if (!jwtService.isAccessToken(claims)) {
                reject(session, "INVALID_TOKEN", "Access token attendu.");
                return;
            }
            User user = userRepository.findById(jwtService.extractUserId(claims))
                    .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                    .orElse(null);
            if (user == null) {
                reject(session, "UNAUTHENTICATED", "Compte introuvable ou inactif.");
                return;
            }
            authenticatedUsers.put(session.getId(), user);
        } catch (JwtException | IllegalArgumentException ex) {
            reject(session, "INVALID_TOKEN", "Token JWT invalide ou expiré.");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        User sender = authenticatedUsers.get(session.getId());
        if (sender == null) {
            reject(session, "UNAUTHENTICATED", "Session non authentifiée.");
            return;
        }
        String payload = message.getPayload();
        if (payload == null || payload.length() > MAX_FRAME_LENGTH) {
            sendError(session, "INVALID_FRAME", "Trame vide ou trop volumineuse.");
            return;
        }
        try {
            WebSocketFrame frame = objectMapper.readValue(payload, WebSocketFrame.class);
            if (frame == null || frame.type() == null) {
                sendError(session, "INVALID_FRAME", "Champ type obligatoire.");
                return;
            }
            if ("SUBSCRIBE".equalsIgnoreCase(frame.type())) {
                if (frame.bookingId() == null) {
                    sendError(session, "INVALID_FRAME", "bookingId obligatoire.");
                    return;
                }
                messagingService.requireBookingParticipant(sender, frame.bookingId());
                broadcaster.subscribe(frame.bookingId(), session);
                send(session, objectMapper.writeValueAsString(
                        new WebSocketAck("SUBSCRIBED", frame.bookingId())));
            } else if ("MESSAGE".equalsIgnoreCase(frame.type())) {
                messagingService.send(sender, new MessageRequest(frame.bookingId(), frame.content()));
            } else {
                sendError(session, "INVALID_FRAME_TYPE", "Type de trame inconnu.");
            }
        } catch (ApiException ex) {
            sendError(session, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            sendError(session, "INVALID_FRAME", "Trame WebSocket invalide.");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        disconnect(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        disconnect(session);
    }

    private void disconnect(WebSocketSession session) {
        authenticatedUsers.remove(session.getId());
        broadcaster.unsubscribe(session);
    }

    private String extractToken(WebSocketSession session) {
        String header = session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return tokenFromQuery(session.getUri());
    }

    private String tokenFromQuery(URI uri) {
        if (uri == null || uri.getRawQuery() == null) {
            return null;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "token".equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void reject(WebSocketSession session, String code, String message) {
        try {
            send(session, objectMapper.writeValueAsString(
                    ApiErrorResponse.of(code, message, List.of())));
        } catch (Exception ignored) {
            // Session déjà fermée : rien à notifier.
        }
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(code));
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        try {
            send(session, objectMapper.writeValueAsString(
                    ApiErrorResponse.of(code, message, List.of())));
        } catch (Exception ignored) {
            // Best-effort : une erreur de diffusion ne doit pas fermer la session.
        }
    }

    private void send(WebSocketSession session, String payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }
}
