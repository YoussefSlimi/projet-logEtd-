package org.logetd.dto;

import org.logetd.domain.admin.AuditLog;
import org.logetd.shared.AuditAction;

import java.time.Instant;

public record AuditLogResponse(Long id,
                               Long actorId,
                               AuditAction action,
                               String targetType,
                               Long targetId,
                               String details,
                               Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorId(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getDetails(), log.getCreatedAt());
    }
}
