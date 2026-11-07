package com.kitchensink.exception;

import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.validation.ValidationException;
import com.mongodb.MongoWriteException;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Converts application and persistence exceptions into consistent HTTP responses.
 *
 * @author Deepak Bheemarasetty
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  /** Returns validation failures using the documented 422 response status. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleRequestStatus(
      ResponseStatusException exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    return ResponseEntity.status(status).body(error(status, exception.getReason()));
  }

  /**
   * Returns validation failures when illegal arguments are encountered.
   *
   * @param exception validation exception
   * @return unprocessable entity response
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException exception) {
    return ResponseEntity.unprocessableEntity()
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    "VALIDATION_FAILED",
                    "message",
                    "Request validation failed",
                    "fields",
                    List.of(
                        Map.of(
                            "field",
                            "request",
                            "message",
                            exception.getMessage() == null
                                ? "is invalid"
                                : exception.getMessage())),
                    "requestId",
                    MDC.get("requestId") == null ? "unknown" : MDC.get("requestId"))));
  }

  /**
   * Returns validation failures when field-level constraints fail.
   *
   * @param exception validation exception
   * @return unprocessable entity response
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, Object>> handleFieldValidation(ValidationException exception) {
    return ResponseEntity.unprocessableEntity()
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    "VALIDATION_FAILED",
                    "message",
                    "Request validation failed",
                    "fields",
                    List.of(
                        Map.of("field", exception.getField(), "message", exception.getMessage())),
                    "requestId",
                    MDC.get("requestId") == null ? "unknown" : MDC.get("requestId"))));
  }

  /**
   * Returns a forbidden response when access is denied.
   *
   * @param exception access denied exception
   * @return forbidden response
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(error(HttpStatus.FORBIDDEN, "You do not have permission"));
  }

  /** Returns a conflict response when an email is already registered. */
  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<Map<String, Object>> handleDuplicateEmail(
      DuplicateEmailException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(error(HttpStatus.CONFLICT, "A member with this email already exists"));
  }

  /** Returns bad request for MongoDB schema failures and conflict for other integrity failures. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    if (containsMongoSchemaViolation(exception)) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              error(HttpStatus.INTERNAL_SERVER_ERROR, "A data integrity constraint was violated"));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(error(HttpStatus.CONFLICT, "The request conflicts with existing data"));
  }

  private Map<String, Object> error(HttpStatus status, String message) {
    return Map.of(
        "error",
        Map.of(
            "code", status == HttpStatus.UNPROCESSABLE_ENTITY ? "VALIDATION_FAILED" : status.name(),
            "message", message == null ? "Request failed" : message,
            "fields", List.of(),
            "requestId", MDC.get("requestId") == null ? "unknown" : MDC.get("requestId")));
  }

  private boolean containsMongoSchemaViolation(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof MongoWriteException writeException
          && writeException.getError().getCode() == 121) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
