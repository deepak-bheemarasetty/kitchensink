package com.kitchensink.model;

import java.io.Serializable;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Base entity providing standard auditing and versioning metadata.
 *
 * @author Deepak Bheemarasetty
 */
@SuppressWarnings("serial")
public abstract class BaseEntity implements Serializable {
  private Instant createdAt;
  private ObjectId createdBy;
  private Instant updatedAt;
  private ObjectId updatedBy;
  private Integer version;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public ObjectId getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(ObjectId createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public ObjectId getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(ObjectId updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
