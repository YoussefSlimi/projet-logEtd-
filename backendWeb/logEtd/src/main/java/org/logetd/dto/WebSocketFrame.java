package org.logetd.dto;

/** Trame entrante du WebSocket brut : {@code SUBSCRIBE} ou {@code MESSAGE}. */
public record WebSocketFrame(String type, Long bookingId, String content) {
}
