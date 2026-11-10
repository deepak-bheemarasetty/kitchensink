package com.kitchensink.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.validation.ValidationException;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for GlobalExceptionHandler.
 *
 * @author Deepak Bheemarasetty
 */
class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleResponseStatusException() {
    ResponseStatusException ex =
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    ResponseEntity<Map<String, Object>> res = handler.handleRequestStatus(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody()).isNotNull();
  }

  @Test
  void handleValidationException() {
    ValidationException ex = new ValidationException("firstName", "must not be blank");
    ResponseEntity<Map<String, Object>> res = handler.handleFieldValidation(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void handleIllegalArgumentException() {
    IllegalArgumentException ex = new IllegalArgumentException("invalid argument");
    ResponseEntity<Map<String, Object>> res = handler.handleValidation(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    IllegalArgumentException nullMsg = new IllegalArgumentException((String) null);
    assertThat(handler.handleValidation(nullMsg).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void handleAccessDeniedException() {
    AccessDeniedException ex = new AccessDeniedException("Forbidden");
    ResponseEntity<Map<String, Object>> res = handler.handleAccessDenied(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void handleDuplicateEmailException() {
    DuplicateEmailException ex = new DuplicateEmailException("Email exists");
    ResponseEntity<Map<String, Object>> res = handler.handleDuplicateEmail(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void handleDataIntegrityViolationExceptionSchemaFailure() {
    MongoWriteException writeException = mock(MongoWriteException.class);
    WriteError writeError = mock(WriteError.class);
    when(writeException.getError()).thenReturn(writeError);
    when(writeError.getCode()).thenReturn(121);

    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("schema violation", writeException);
    ResponseEntity<Map<String, Object>> res = handler.handleDataIntegrityViolation(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void handleDataIntegrityViolationExceptionBulkFailure() {
    MongoBulkWriteException bulkException = mock(MongoBulkWriteException.class);
    when(bulkException.getWriteErrors()).thenReturn(java.util.Collections.emptyList());

    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("bulk failure", bulkException);
    ResponseEntity<Map<String, Object>> res = handler.handleDataIntegrityViolation(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void handleDataIntegrityViolationExceptionOther() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("conflict", new RuntimeException());
    ResponseEntity<Map<String, Object>> res = handler.handleDataIntegrityViolation(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void handleGenericException() {
    Exception ex = new Exception("unexpected");
    ResponseEntity<Map<String, Object>> res = handler.handleGenericException(ex);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
