package com.kitchensink.exception;

import com.kitchensink.service.DuplicateEmailException;
import com.mongodb.MongoWriteException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts application and persistence exceptions into consistent HTTP responses.
 *
 * @author Deepak Bheemarasetty
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  /** Returns a conflict response when an email is already registered. */
  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateEmail(
      DuplicateEmailException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("email", "Email taken"));
  }

  /** Returns bad request for MongoDB schema failures and conflict for other integrity failures. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    if (containsMongoSchemaViolation(exception)) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "The member does not satisfy MongoDB schema constraints"));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "The request conflicts with existing data"));
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
