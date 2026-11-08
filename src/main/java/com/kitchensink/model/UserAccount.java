package com.kitchensink.model;

import com.kitchensink.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Application login account, separate from directory members. */
@SuppressWarnings("serial")
@Document(collection = "users")
public class UserAccount extends BaseEntity {
  @Id private ObjectId id;
  private String username;
  private String usernameLower;
  private List<String> roles;
  private boolean isActive;
  private Instant lastLoginAt;

  public ObjectId getId() {
    return id;
  }

  public void setId(ObjectId id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getUsernameLower() {
    return usernameLower;
  }

  public void setUsernameLower(String usernameLower) {
    this.usernameLower = usernameLower;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles == null ? null : Collections.unmodifiableList(new ArrayList<>(roles));
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    isActive = active;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  public void validate() {
    Validator.userAccount(this);
  }
}
