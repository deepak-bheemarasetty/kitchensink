package com.kitchensink.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

  @Test
  void mapsMongoSchemaViolationsToBadRequest() {
    MongoWriteException mongoException = mock(MongoWriteException.class);
    WriteError writeError = mock(WriteError.class);
    when(mongoException.getError()).thenReturn(writeError);
    when(writeError.getCode()).thenReturn(121);

    var response =
        exceptionHandler.handleDataIntegrityViolation(
            new DataIntegrityViolationException("Document failed validation", mongoException));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .containsEntry("error", "The member does not satisfy MongoDB schema constraints");
  }

  @Test
  void mapsOtherIntegrityViolationsToConflict() {
    MongoWriteException mongoException = mock(MongoWriteException.class);
    WriteError writeError = mock(WriteError.class);
    when(mongoException.getError()).thenReturn(writeError);
    when(writeError.getCode()).thenReturn(11000);

    var response =
        exceptionHandler.handleDataIntegrityViolation(
            new DataIntegrityViolationException("Unique index conflict", mongoException));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody())
        .containsEntry("error", "The request conflicts with existing data");
  }
}
