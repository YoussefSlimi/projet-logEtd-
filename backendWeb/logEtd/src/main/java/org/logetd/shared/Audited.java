package org.logetd.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Déclare l'écriture automatique d'une entrée de journal après l'exécution de la méthode.
 * Les expressions sont du SpEL : {@code #result} est la valeur de retour, {@code #args} les arguments.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction action();

    String targetType();

    /** Expression SpEL donnant l'identifiant de la cible (ex. {@code #result.id()}). */
    String targetId();

    /** Expression SpEL facultative pour les détails (ex. {@code #args[1].reason()}). */
    String details() default "";
}
