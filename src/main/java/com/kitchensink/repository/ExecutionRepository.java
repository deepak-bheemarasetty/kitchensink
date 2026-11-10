package com.kitchensink.repository;

import com.kitchensink.model.Execution;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Execution document operations.
 *
 * @author Deepak Bheemarasetty
 */
@Repository
public interface ExecutionRepository extends MongoRepository<Execution, ObjectId> {

  /**
   * Finds executions by user email.
   *
   * @param userEmail the user email
   * @return list of executions
   */
  List<Execution> findByUserEmail(String userEmail);

  /**
   * Finds executions by event name and user email.
   *
   * @param eventName the event name
   * @param userEmail the user email
   * @return list of executions
   */
  List<Execution> findByEventNameAndUserEmail(String eventName, String userEmail);

  /**
   * Finds an execution by request ID.
   *
   * @param requestId the request correlation ID
   * @return list of executions
   */
  List<Execution> findByRequestId(String requestId);
}
