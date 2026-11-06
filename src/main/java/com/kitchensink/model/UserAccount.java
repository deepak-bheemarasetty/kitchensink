package com.kitchensink.model;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Application login account, separate from directory members. */
@Document(collection = "users")
public class UserAccount {
  private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,31}$");
  private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$");
  @Id private ObjectId id;
  private String username;
  private String usernameLower;
  private String passwordHash;
  private List<String> roles;
  private boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastLoginAt;

  public ObjectId getId() { return id; }
  public void setId(ObjectId id) { this.id = id; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getUsernameLower() { return usernameLower; }
  public void setUsernameLower(String usernameLower) { this.usernameLower = usernameLower; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public List<String> getRoles() { return roles; }
  public void setRoles(List<String> roles) { this.roles = roles == null ? null : List.copyOf(roles); }
  public boolean isActive() { return isActive; }
  public void setActive(boolean active) { isActive = active; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public Instant getLastLoginAt() { return lastLoginAt; }
  public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

  /** Validates persisted account invariants before save or after loading from storage. */
  public void validate() {
    if (username == null || !USERNAME.matcher(username).matches()) throw new IllegalStateException("Username must be 3 to 32 valid characters");
    if (!username.toLowerCase(Locale.ROOT).equals(usernameLower)) throw new IllegalStateException("usernameLower must match username");
    if (passwordHash == null || (!BCRYPT.matcher(passwordHash).matches() && !passwordHash.startsWith("$argon2id$"))) throw new IllegalStateException("Password must be stored as a BCrypt or Argon2id hash");
    if (roles == null || roles.isEmpty() || roles.size() > 10 || new HashSet<>(roles).size() != roles.size()) throw new IllegalStateException("User must have 1 to 10 unique roles");
    if (roles.stream().anyMatch(role -> role == null || role.isBlank())) throw new IllegalStateException("Role names cannot be blank");
    if (createdAt == null || updatedAt == null) throw new IllegalStateException("User timestamps are required");
  }
}
