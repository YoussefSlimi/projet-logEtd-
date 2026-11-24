package org.logetd.dto;

/** Accusé de réception envoyé après un abonnement WebSocket valide. */
public record WebSocketAck(String type, Long bookingId) {
}
