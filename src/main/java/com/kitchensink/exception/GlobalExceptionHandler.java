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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
    String detail = exception.getMessage() == null ? "is invalid" : exception.getMessage();
    return ResponseEntity.unprocessableEntity()
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    "VALIDATION_FAILED",
                    "message",
                    "Request validation failed: " + detail,
                    "fields",
                    List.of(Map.of("field", "request", "message", detail)),
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
    String detail = exception.getMessage() == null ? "is invalid" : exception.getMessage();
    return ResponseEntity.unprocessableEntity()
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    "VALIDATION_FAILED",
                    "message",
                    "Request validation failed: " + detail,
                    "fields",
                    List.of(Map.of("field", exception.getField(), "message", detail)),
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
   * Returns a 400 Bad Request when request body cannot be parsed or JSON is malformed.
   *
   * @param exception message not readable exception
   * @return bad request response
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleMessageNotReadable(
      HttpMessageNotReadableException exception) {
    return ResponseEntity.badRequest()
        .body(error(HttpStatus.BAD_REQUEST, "Malformed JSON or unreadable request body"));
  }

  /**
   * Returns a 400 Bad Request when a required header (such as Idempotency-Key) is missing.
   *
   * @param exception missing header exception
   * @return bad request response
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<Map<String, Object>> handleMissingHeader(
      MissingRequestHeaderException exception) {
    return ResponseEntity.badRequest()
        .body(
            error(HttpStatus.BAD_REQUEST, "Required header missing: " + exception.getHeaderName()));
  }

  /**
   * Returns a 400 Bad Request when path variable or query parameter format is invalid.
   *
   * @param exception type mismatch exception
   * @return bad request response
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    String paramName = exception.getName();
    return ResponseEntity.badRequest()
        .body(error(HttpStatus.BAD_REQUEST, "Invalid format for parameter: " + paramName));
  }

  /**
   * Returns a 405 Method Not Allowed when an unsupported HTTP method is invoked on an endpoint.
   *
   * @param exception method not supported exception
   * @return method not allowed response
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException exception) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            error(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not allowed: " + exception.getMethod()));
  }

  /**
   * Returns a 415 Unsupported Media Type when payload content-type is unsupported.
   *
   * @param exception media type not supported exception
   * @return unsupported media type response
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException exception) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(
            error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type: " + exception.getContentType()));
  }

  /**
   * Catches any unhandled exceptions to prevent stack trace leakage to clients.
   *
   * @param exception unhandled exception
   * @return internal server error response with sanitized message
   */
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception exception) {
    return handleGenericException(exception, null);
  }

  /**
   * Catches any unhandled exceptions to prevent stack trace leakage to clients.
   *
   * @param exception unhandled exception
   * @param request current http servlet request
   * @return internal server error response with sanitized message
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(
      Exception exception, jakarta.servlet.http.HttpServletRequest request) {
    LOGGER.error("Unhandled exception processing request: ", exception);
    if (request != null) {
      String detail = exception.getMessage();
      if (detail == null || detail.isBlank()) {
        detail = exception.getClass().getSimpleName();
      }
      request.setAttribute("EXECUTION_ERROR_DETAIL", detail);
    }
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
