package com.kitchensink.model;

import com.kitchensink.validation.Validator;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

/**
 * Immutable history record for security and member changes.
 *
 * @author Deepak Bheemarasetty
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "audit_logs")
public class AuditLog {
  @Id private ObjectId id;
  private Instant at;
  private ObjectId actorId;
  private String action;
  private String entityType;
  private ObjectId entityId;
  private Map<String, Object> changes;
  private String requestId;
  private String ip;

  public ObjectId getId() {
    return id;
  }

  public void setId(ObjectId id) {
    this.id = id;
  }

  public Instant getAt() {
    return at;
  }

  public void setAt(Instant at) {
    this.at = at;
  }

  public ObjectId getActorId() {
    return actorId;
  }

  public void setActorId(ObjectId actorId) {
    this.actorId = actorId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public ObjectId getEntityId() {
    return entityId;
  }

  public void setEntityId(ObjectId entityId) {
    this.entityId = entityId;
  }

  public Map<String, Object> getChanges() {
    return changes;
  }

  public void setChanges(Map<String, Object> changes) {
    this.changes =
        changes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(changes));
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  /** Validates an audit record before its insert-only persistence operation. */
  public void validateForInsert() {
    Validator.auditLog(this);
  }
}
