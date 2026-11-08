package com.kitchensink.service;

import com.kitchensink.model.Role;
import com.kitchensink.model.UserAccount;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Resolves effective permissions from the application's roles collection. */
@Service
public class RolePermissionService {
  private final MongoTemplate mongoTemplate;

  public RolePermissionService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Resolves effective permissions for a list of role names.
   *
   * @param roleNames list of role names
   * @return set of permissions
   */
  public Set<String> permissionsFor(List<String> roleNames) {
    Set<String> permissions = new LinkedHashSet<>();
    if (roleNames == null || roleNames.isEmpty()) {
      return permissions;
    }
    Query query = Query.query(Criteria.where("name").in(roleNames));
    for (Role role : mongoTemplate.find(query, Role.class)) {
      role.validate();
      permissions.addAll(role.getPermissions());
    }
    return permissions;
  }

  /**
   * Resolves active status and permissions for a given username.
   *
   * @param username user account username or email
   * @return user permissions model
   */
  public UserPermissions user(String username) {
    if (username == null || username.isBlank()) {
      return new UserPermissions(false, Set.of());
    }
    UserAccount user =
        mongoTemplate.findOne(
            Query.query(Criteria.where("usernameLower").is(username.toLowerCase(Locale.ROOT))),
            UserAccount.class);
    if (user == null || !user.isActive()) {
      return new UserPermissions(false, Set.of());
    }
    try {
      user.validate();
    } catch (com.kitchensink.validation.ValidationException ex) {
      return new UserPermissions(false, Set.of());
    }
    return new UserPermissions(true, permissionsFor(user.getRoles()));
  }

  /** Representation of user active status and their effective permission set. */
  public record UserPermissions(boolean active, Set<String> permissions) {}
}
