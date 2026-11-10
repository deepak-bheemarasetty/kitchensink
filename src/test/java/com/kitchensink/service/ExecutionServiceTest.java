package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Execution;
import com.kitchensink.repository.ExecutionRepository;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ExecutionService.
 *
 * @author Deepak Bheemarasetty
 */
class ExecutionServiceTest {

  private ExecutionRepository repository;
  private ExecutionService service;

  @BeforeEach
  void setUp() {
    repository = mock(ExecutionRepository.class);
    service = new ExecutionService(repository);
  }

  @Test
  void recordReceivedSavesExecutionSuccessfully() {
    when(repository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Execution execution =
        service.recordReceived("user@example.com", "member.create", "POST", "req-123");

    assertThat(execution).isNotNull();
    assertThat(execution.getUserEmail()).isEqualTo("user@example.com");
    assertThat(execution.getEventName()).isEqualTo("member.create");
    assertThat(execution.getHttpMethod()).isEqualTo("POST");
    assertThat(execution.getRequestId()).isEqualTo("req-123");
    assertThat(execution.getStatus()).isEqualTo("RECEIVED");
    assertThat(execution.getErrorMessage()).isNull();
    assertThat(execution.getCreatedAt()).isNotNull();
    assertThat(execution.getUpdatedAt()).isNotNull();
  }

  @Test
  void recordReceivedHandlesRepositoryExceptionGracefully() {
    when(repository.save(any(Execution.class))).thenThrow(new RuntimeException("DB error"));

    Execution execution =
        service.recordReceived("user@example.com", "member.create", "POST", "req-123");

    assertThat(execution).isNull();
  }

  @Test
  void recordCompletionUpdatesExistingExecution() {
    ObjectId id = new ObjectId();
    Execution execution = new Execution();
    execution.setId(id);
    execution.setStatus("RECEIVED");

    when(repository.findById(id)).thenReturn(Optional.of(execution));
    when(repository.save(any(Execution.class))).thenReturn(execution);

    service.recordCompletion(id, "SUCCESS", null);

    assertThat(execution.getStatus()).isEqualTo("SUCCESS");
    assertThat(execution.getErrorMessage()).isNull();
    verify(repository).save(execution);
  }

  @Test
  void recordCompletionHandlesFailureAndException() {
    ObjectId id = new ObjectId();
    Execution execution = new Execution();
    execution.setId(id);

    when(repository.findById(id)).thenReturn(Optional.of(execution));
    when(repository.save(any(Execution.class))).thenThrow(new RuntimeException("DB error"));

    // Should not throw
    service.recordCompletion(id, "FAILED", "Something went wrong");

    // Null executionId should be no-op
    service.recordCompletion(null, "SUCCESS", null);

    // Missing execution should be no-op
    when(repository.findById(id)).thenReturn(Optional.empty());
    service.recordCompletion(id, "SUCCESS", null);
  }
}
