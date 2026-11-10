package com.kitchensink.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Execution model getters and setters.
 *
 * @author Deepak Bheemarasetty
 */
class ExecutionTest {

  @Test
  void testExecutionGettersAndSetters() {
    Execution execution = new Execution();
    ObjectId id = new ObjectId();
    Instant now = Instant.now();

    execution.setId(id);
    execution.setUserEmail("user@example.com");
    execution.setEventName("member.create");
    execution.setHttpMethod("POST");
    execution.setRequestId("req-123");
    execution.setStatus("RECEIVED");
    execution.setErrorMessage("test error");
    execution.setCreatedAt(now);
    execution.setUpdatedAt(now);

    assertThat(execution.getId()).isEqualTo(id);
    assertThat(execution.getUserEmail()).isEqualTo("user@example.com");
    assertThat(execution.getEventName()).isEqualTo("member.create");
    assertThat(execution.getHttpMethod()).isEqualTo("POST");
    assertThat(execution.getRequestId()).isEqualTo("req-123");
    assertThat(execution.getStatus()).isEqualTo("RECEIVED");
    assertThat(execution.getErrorMessage()).isEqualTo("test error");
    assertThat(execution.getCreatedAt()).isEqualTo(now);
    assertThat(execution.getUpdatedAt()).isEqualTo(now);
  }
}
