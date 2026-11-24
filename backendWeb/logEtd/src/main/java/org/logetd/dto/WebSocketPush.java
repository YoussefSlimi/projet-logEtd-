package org.logetd.dto;

/** Trame sortante diffusée aux abonnés d'une réservation. */
public record WebSocketPush(String type, Long bookingId, MessageResponse message) {
}
