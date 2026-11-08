package com.kitchensink.model;

import com.kitchensink.validation.Validator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Named permission bundle assigned to application users. */
@SuppressWarnings("serial")
@Document(collection = "roles")
public class Role extends BaseEntity {
  @Id private ObjectId id;
  private String name;
  private String description;
  private List<String> permissions;
  private boolean isSystem;

  public ObjectId getId() {
    return id;
  }

  public void setId(ObjectId id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<String> permissions) {
    this.permissions =
        permissions == null ? null : Collections.unmodifiableList(new ArrayList<>(permissions));
  }

  public boolean isSystem() {
    return isSystem;
  }

  public void setSystem(boolean system) {
    isSystem = system;
  }

  /** Validates role fields and the member-write permission dependency. */
  public void validate() {
    Validator.role(this);
  }
}
