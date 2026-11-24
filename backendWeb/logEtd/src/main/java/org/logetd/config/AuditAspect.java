package org.logetd.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.logetd.domain.user.User;
import org.logetd.service.AuditService;
import org.logetd.shared.Audited;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Intercepte les méthodes {@link Audited} et écrit le journal après leur succès. */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        Object[] args = joinPoint.getArgs();
        Long targetId = evaluateLong(audited.targetId(), args, result);
        if (targetId == null) {
            throw new IllegalStateException("Cible d'audit introuvable : " + audited.action());
        }
        auditService.log(audited.action(), audited.targetType(), targetId,
                currentActorId(), evaluateString(audited.details(), args, result));
        return result;
    }

    private Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    private Long evaluateLong(String expression, Object[] args, Object result) {
        Object value = evaluate(expression, args, result);
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private String evaluateString(String expression, Object[] args, Object result) {
        Object value = evaluate(expression, args, result);
        return value == null ? null : value.toString();
    }

    private Object evaluate(String expression, Object[] args, Object result) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", args);
        context.setVariable("result", result);
        try {
            return parser.parseExpression(expression).getValue(context);
        } catch (EvaluationException ex) {
            // Expression non évaluable (ex. résultat nul) : le contrôle de cible ci-dessus
            // produira une erreur explicite plutôt qu'une fuite de la cause SpEL.
            return null;
        }
    }
}
