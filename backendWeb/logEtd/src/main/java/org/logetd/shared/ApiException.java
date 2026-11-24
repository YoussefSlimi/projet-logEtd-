package org.logetd.shared;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Erreur métier avec un code stable, traduite en JSON par le gestionnaire global. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiErrorResponse.FieldIssue> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message,
                        List<ApiErrorResponse.FieldIssue> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiErrorResponse.FieldIssue> getDetails() {
        return details;
    }
}
