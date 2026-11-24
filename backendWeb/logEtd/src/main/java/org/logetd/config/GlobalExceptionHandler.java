package org.logetd.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldIssue> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldIssue(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "Données d'entrée invalides.", details));
    }

    /** Une violation de contrainte SQL est un conflit métier, jamais un 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? null : cause.getMessage();
        boolean overlap = message != null && message.contains("no_overlap_active_booking");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                overlap ? "BOOKING_DATES_UNAVAILABLE" : "DATA_CONFLICT",
                overlap ? "Ces dates ne sont plus disponibles."
                        : "La ressource est en conflit avec l'état de la base.",
                overlap ? List.of(new ApiErrorResponse.FieldIssue("startDate", "overlap")) : List.of()));
    }

    /** Un deadlock sur la contrainte d'exclusion est également un conflit de dates. */
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentBooking(CannotAcquireLockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                "BOOKING_DATES_UNAVAILABLE", "Ces dates ne sont plus disponibles.",
                List.of(new ApiErrorResponse.FieldIssue("startDate", "overlap"))));
    }

    /** Paramètres de requête ou parties multipart absents / invalides : 400, jamais 500. */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequestParameter(Exception ex) {
        String field = "request";
        if (ex instanceof MissingServletRequestParameterException missing) {
            field = missing.getParameterName();
        } else if (ex instanceof MissingServletRequestPartException missing) {
            field = missing.getRequestPartName();
        } else if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            field = mismatch.getName();
        }
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR", "Paramètre de requête invalide ou manquant.",
                List.of(new ApiErrorResponse.FieldIssue(field, "valeur requise invalide"))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        // Aucune donnée personnelle n'est écrite dans les logs.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "Une erreur inattendue est survenue.", List.of()));
    }

    /** Écrit une erreur JSON pour les réponses 401/403 produites par Spring Security. */
    public static void writeSecurityError(HttpServletResponse response, ObjectMapper mapper,
                                          HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message, List.of()));
    }
}
