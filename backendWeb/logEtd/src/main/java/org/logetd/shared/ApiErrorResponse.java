package org.logetd.shared;

import java.time.Instant;
import java.util.List;

/** Format d'erreur standard du contrat d'API. */
public record ApiErrorResponse(String code,
                               String message,
                               List<FieldIssue> details,
                               Instant timestamp) {

    public record FieldIssue(String field, String issue) {
    }

    public static ApiErrorResponse of(String code, String message, List<FieldIssue> details) {
        return new ApiErrorResponse(code, message, details == null ? List.of() : details, Instant.now());
    }
}
