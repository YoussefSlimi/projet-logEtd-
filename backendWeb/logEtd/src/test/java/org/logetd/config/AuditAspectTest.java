package org.logetd.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.logetd.domain.user.User;
import org.logetd.dto.MessageResponse;
import org.logetd.service.AuditService;
import org.logetd.shared.AuditAction;
import org.logetd.shared.Audited;
import org.logetd.shared.Role;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private final AuditService auditService = Mockito.mock(AuditService.class);
    private final AuditAspect aspect = new AuditAspect(auditService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesAuditEntryWithActorAndTargetFromAnnotatedMethod() throws Throwable {
        authenticate(1L);
        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        User sender = user(1L);
        when(joinPoint.proceed()).thenReturn(new MessageResponse(
                11L, 7L, 1L, "Bonjour", Instant.now(), null));
        when(joinPoint.getArgs()).thenReturn(new Object[]{sender, "ignoré"});

        aspect.record(joinPoint, annotation("annotatedMethod"));

        verify(auditService).log(AuditAction.MESSAGE_SENT, "Message", 11L, 1L, null);
    }

    @Test
    void capturesRefusalReasonFromArguments() throws Throwable {
        authenticate(100L);
        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{user(100L), 5L, "Photos trompeuses"});

        aspect.record(joinPoint, annotation("decisionMethod"));

        verify(auditService).log(AuditAction.LISTING_REFUSED, "Listing", 5L, 100L, "Photos trompeuses");
    }

    @Test
    void refusesToWriteEntryWithoutTarget() throws Throwable {
        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        assertThrows(IllegalStateException.class,
                () -> aspect.record(joinPoint, annotation("annotatedMethod")));
        verify(auditService, never()).log(anyAction(), any(), any(), any(), any());
    }

    private AuditAction anyAction() {
        return org.mockito.ArgumentMatchers.any(AuditAction.class);
    }

    private void authenticate(Long id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user(id), null, List.of()));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.ADMIN);
        return user;
    }

    private Audited annotation(String methodName) {
        Method method;
        try {
            method = AuditAspectTest.class.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
        return method.getAnnotation(Audited.class);
    }

    @Audited(action = AuditAction.MESSAGE_SENT, targetType = "Message", targetId = "#result.id()")
    private void annotatedMethod() {
    }

    @Audited(action = AuditAction.LISTING_REFUSED, targetType = "Listing",
            targetId = "#args[1]", details = "#args[2]")
    private void decisionMethod() {
    }
}
