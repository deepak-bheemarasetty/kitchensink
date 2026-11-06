package com.kitchensink.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Named permission bundle assigned to application users. */
@Document(collection = "roles")
public class Role {
  private static final Pattern NAME = Pattern.compile("^[a-z0-9-]{2,30}$");
  private static final Set<String> ALLOWED_PERMISSIONS = Set.of(
      "member:view", "member:create", "member:update", "member:delete", "user:manage", "role:manage");
  @Id private ObjectId id;
  private String name;
  private String description;
  private List<String> permissions;
  private boolean isSystem;
  private Instant updatedAt;

  public ObjectId getId() { return id; }
  public void setId(ObjectId id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public List<String> getPermissions() { return permissions; }
  public void setPermissions(List<String> permissions) { this.permissions = permissions == null ? null : List.copyOf(permissions); }
  public boolean isSystem() { return isSystem; }
  public void setSystem(boolean system) { isSystem = system; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  /** Validates role fields and the member-write permission dependency. */
  public void validate() {
    if (name == null || !NAME.matcher(name).matches()) throw new IllegalStateException("Role name is invalid");
    if (description == null || description.length() > 200) throw new IllegalStateException("Role description is invalid");
    if (permissions == null || permissions.isEmpty() || new HashSet<>(permissions).size() != permissions.size()
        || !ALLOWED_PERMISSIONS.containsAll(permissions)) throw new IllegalStateException("Role permissions are invalid");
    if (permissions.stream().anyMatch(permission -> Set.of("member:create", "member:update", "member:delete").contains(permission))
        && !permissions.contains("member:view")) throw new IllegalStateException("Member write permissions require member:view");
    if (updatedAt == null) throw new IllegalStateException("Role updatedAt is required");
  }
}
