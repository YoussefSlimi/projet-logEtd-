package org.logetd.dto;

import org.logetd.domain.messaging.Message;

import java.time.Instant;

public record MessageResponse(Long id,
                              Long bookingId,
                              Long senderId,
                              String content,
                              Instant sentAt,
                              Instant readAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getBooking().getId(),
                message.getSender().getId(),
                message.getContent(),
                message.getSentAt(),
                message.getReadAt());
    }
}
