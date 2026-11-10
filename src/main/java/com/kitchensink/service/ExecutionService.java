package com.kitchensink.service;

import com.kitchensink.model.Execution;
import com.kitchensink.repository.ExecutionRepository;
import java.time.Instant;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing Execution document lifecycles outside of outer request transactions.
 *
 * @author Deepak Bheemarasetty
 */
@Service
public class ExecutionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionService.class);

  private final ExecutionRepository executionRepository;

  public ExecutionService(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  /**
   * Records a new execution with RECEIVED status in an independent transaction.
   *
   * @param userEmail authenticated user email
   * @param eventName mapped event name for the endpoint
   * @param httpMethod HTTP method
   * @param requestId correlation request ID
   * @return newly created Execution
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Execution recordReceived(
      String userEmail, String eventName, String httpMethod, String requestId) {
    Execution execution = new Execution();
    execution.setUserEmail(userEmail);
    execution.setEventName(eventName);
    execution.setHttpMethod(httpMethod);
    execution.setRequestId(requestId);
    execution.setStatus("RECEIVED");
    execution.setErrorMessage(null);
    Instant now = Instant.now();
    execution.setCreatedAt(now);
    execution.setUpdatedAt(now);
    try {
      return executionRepository.save(execution);
    } catch (Exception ex) {
      LOGGER.warn("Failed to record execution RECEIVED state: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * Updates an existing execution to SUCCESS or FAILED in an independent transaction.
   *
   * @param executionId ObjectId of the execution
   * @param status final status (SUCCESS or FAILED)
   * @param errorMessage error message if failed, or null if success
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void recordCompletion(ObjectId executionId, String status, String errorMessage) {
    if (executionId == null) {
      return;
    }
    try {
      Optional<Execution> optional = executionRepository.findById(executionId);
      if (optional.isPresent()) {
        Execution execution = optional.get();
        execution.setStatus(status);
        execution.setErrorMessage(errorMessage);
        execution.setUpdatedAt(Instant.now());
        executionRepository.save(execution);
      }
    } catch (Exception ex) {
      LOGGER.warn("Failed to record execution completion state: {}", ex.getMessage());
    }
  }
}
