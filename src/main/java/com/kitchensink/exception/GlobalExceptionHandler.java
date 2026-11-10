package com.kitchensink.exception;

import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.validation.ValidationException;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    MongoWriteException writeException = findMongoWriteException(exception);
    if (writeException != null && writeException.getError().getCode() == 121) {
      LOGGER.error(
          "MongoDB rejected a document schema. requestId={}, code={}, details={}",
          MDC.get("requestId"),
          writeException.getError().getCode(),
          writeException.getError().getDetails());
      return ResponseEntity.badRequest()
          .body(
              error(
                  HttpStatus.BAD_REQUEST,
                  "A database document does not satisfy MongoDB schema constraints"));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(error(HttpStatus.CONFLICT, "The request conflicts with existing data"));
  }

  /**
   * Catches any unhandled exceptions to prevent stack trace leakage to clients.
   *
   * @param exception unhandled exception
   * @return internal server error response with sanitized message
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception exception) {
    LOGGER.error("Unhandled exception processing request: ", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Please contact support."));
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

  private MongoWriteException findMongoWriteException(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof MongoWriteException writeException) {
        return writeException;
      }
      if (cause instanceof MongoBulkWriteException bulkWriteException) {
        for (WriteError writeError : bulkWriteException.getWriteErrors()) {
          if (writeError.getCode() == 121) {
            LOGGER.error(
                "MongoDB bulk write rejected a document schema: {}", writeError.getDetails());
          }
        }
      }
      cause = cause.getCause();
    }
    return null;
  }
}
