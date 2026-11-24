package org.logetd.service;

import org.logetd.dto.MessageResponse;
import org.logetd.dto.WebSocketPush;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Diffuse les messages aux sessions WebSocket abonnées à une réservation. */
@Component
public class MessageBroadcaster {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> byBooking = new ConcurrentHashMap<>();

    public MessageBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void subscribe(Long bookingId, WebSocketSession session) {
        byBooking.computeIfAbsent(bookingId, ignored -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unsubscribe(WebSocketSession session) {
        byBooking.values().forEach(sessions -> sessions.remove(session));
        byBooking.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public int subscriberCount(Long bookingId) {
        Set<WebSocketSession> sessions = byBooking.get(bookingId);
        return sessions == null ? 0 : sessions.size();
    }

    /** Envoi best-effort : une session fermée est retirée sans faire échouer l'envoi du message. */
    public void broadcast(Long bookingId, MessageResponse message) {
        Set<WebSocketSession> sessions = byBooking.get(bookingId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(
                    new WebSocketPush("MESSAGE", bookingId, message));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la trame WebSocket", ex);
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            synchronized (session) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException ex) {
                    sessions.remove(session);
                }
            }
        }
    }
}
