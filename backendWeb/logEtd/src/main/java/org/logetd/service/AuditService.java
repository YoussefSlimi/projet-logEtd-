package org.logetd.service;

import org.logetd.domain.admin.AuditLog;
import org.logetd.domain.admin.AuditLogRepository;
import org.logetd.shared.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Écriture centralisée du journal d'audit, appelée par l'aspect AOP et par les services. */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(AuditAction action, String targetType, Long targetId,
                    Long actorId, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setActorId(actorId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }
}
