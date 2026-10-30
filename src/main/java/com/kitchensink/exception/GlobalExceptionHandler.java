package com.kitchensink.exception;

import com.kitchensink.service.DuplicateEmailException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts application and validation exceptions into consistent HTTP responses.
 *
 * @author Deepak Bheemarasetty
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  /** Returns field-level errors for an invalid request body. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      MethodArgumentNotValidException exception) {
    Map<String, String> errors = new TreeMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.badRequest().body(errors);
  }

  /** Returns a conflict response when an email is already registered. */
  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateEmail(
      DuplicateEmailException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("email", "Email taken"));
  }

  /** Returns a generic conflict response for persistence constraint violations. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrityViolation() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "The request conflicts with existing data"));
  }
}
