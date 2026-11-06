package com.kitchensink.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import com.kitchensink.model.Role;
import com.kitchensink.model.UserAccount;
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

  public Set<String> permissionsFor(List<String> roleNames) {
    Set<String> permissions = new LinkedHashSet<>();
    if (roleNames == null || roleNames.isEmpty()) return permissions;
    Query query = Query.query(Criteria.where("name").in(roleNames));
    query.fields().include("permissions");
    for (Role role : mongoTemplate.find(query, Role.class)) {
      role.validate();
      permissions.addAll(role.getPermissions());
    }
    return permissions;
  }

  public UserPermissions user(String username) {
    UserAccount user = mongoTemplate.findOne(
        Query.query(Criteria.where("usernameLower").is(username.toLowerCase(Locale.ROOT))),
        UserAccount.class);
    if (user == null || !user.isActive()) {
      return new UserPermissions(false, Set.of());
    }
    user.validate();
    return new UserPermissions(true, permissionsFor(user.getRoles()));
  }

  public record UserPermissions(boolean active, Set<String> permissions) {}
}
