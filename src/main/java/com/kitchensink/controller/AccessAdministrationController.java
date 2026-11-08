package com.kitchensink.controller;

import com.kitchensink.model.AuditLog;
import com.kitchensink.model.Role;
import com.kitchensink.validation.Validator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Administrative APIs for application roles and local user accounts. */
@RestController
@RequestMapping("/api/v1")
public class AccessAdministrationController {
  private final MongoTemplate mongoTemplate;

  public AccessAdministrationController(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @GetMapping("/roles")
  public List<Document> roles() {
    return mongoTemplate.findAll(Document.class, "roles");
  }

  /**
   * Creates a new role.
   *
   * @param request role creation request
   * @param actor authenticated user performing the action
   * @return created role document
   */
  @PostMapping("/roles")
  public Document createRole(@RequestBody RoleRequest request, Authentication actor) {
    require(actor, "role:manage");
    String name = Validator.roleName(request.name());
    Role validatedRole = new Role();
    validatedRole.setName(name);
    validatedRole.setDescription(Validator.roleDescription(request.description()));
    validatedRole.setPermissions(request.permissions());
    validatedRole.setUpdatedAt(Instant.now());
    validatedRole.validate();
    Document role =
        new Document("name", validatedRole.getName())
            .append("description", validatedRole.getDescription())
            .append("permissions", validatedRole.getPermissions().stream().sorted().toList())
            .append("isSystem", false)
            .append("updatedAt", Date.from(Instant.now()));
    mongoTemplate.insert(role, "roles");
    writeAudit(
        actor,
        "role.update",
        "role",
        role.getObjectId("_id"),
        new Document("created", true).append("permissions", request.permissions()));
    return role;
  }

  /**
   * Updates an existing role by name.
   *
   * @param name role name
   * @param request role update request
   * @param actor authenticated user performing the action
   * @return updated role document
   */
  @PutMapping("/roles/{name}")
  public Document updateRole(
      @PathVariable String name, @RequestBody RoleRequest request, Authentication actor) {
    require(actor, "role:manage");
    Role validatedRole = new Role();
    validatedRole.setName(Validator.roleName(name));
    validatedRole.setDescription(Validator.roleDescription(request.description()));
    validatedRole.setPermissions(request.permissions());
    validatedRole.setUpdatedAt(Instant.now());
    validatedRole.validate();
    Query query = Query.query(Criteria.where("name").is(name));
    Document current = mongoTemplate.findOne(query, Document.class, "roles");
    if (current == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
    }
    current.put("description", validatedRole.getDescription());
    current.put("permissions", validatedRole.getPermissions().stream().sorted().toList());
    current.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(current, "roles");
    writeAudit(
        actor,
        "role.update",
        "role",
        current.getObjectId("_id"),
        new Document("permissions", request.permissions()));
    return current;
  }

  /**
   * Deletes a role by name.
   *
   * @param name role name
   * @param actor authenticated user performing the action
   */
  @DeleteMapping("/roles/{name}")
  public void deleteRole(@PathVariable String name, Authentication actor) {
    require(actor, "role:manage");
    Document role =
        mongoTemplate.findOne(
            Query.query(Criteria.where("name").is(name)), Document.class, "roles");
    if (role == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
    }
    if (role.getBoolean("isSystem", false)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "System roles cannot be deleted");
    }
    long assigned = mongoTemplate.count(Query.query(Criteria.where("roles").is(name)), "users");
    if (assigned > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Role is assigned to " + assigned + " users");
    }
    mongoTemplate.remove(role, "roles");
    writeAudit(
        actor, "role.update", "role", role.getObjectId("_id"), new Document("deleted", true));
  }

  /**
   * Lists all users. The {@code _id} ObjectId is exposed as a plain hex string under the key {@code
   * id} so the browser can pass it back as a URL path segment without serialization issues.
   *
   * @param actor authenticated user performing the action
   * @return list of user documents
   */
  @GetMapping("/users")
  public List<Document> users(Authentication actor) {
    require(actor, "user:manage");
    Query query = new Query();
    List<Document> users = mongoTemplate.find(query, Document.class, "users");
    return users.stream()
        .peek(
            user -> {
              user.put("isSelf", user.getString("username").equalsIgnoreCase(identity(actor)));
              // Expose _id as a plain hex string so the client can use it in URL paths
              org.bson.types.ObjectId oid = user.getObjectId("_id");
              if (oid != null) {
                user.put("id", oid.toHexString());
              }
            })
        .toList();
  }

  /**
   * Registers a new user identity.
   *
   * @param request user identity creation request
   * @param actor authenticated user performing the action
   * @return created user document
   */
  @PostMapping("/users/identity")
  public Document registerIdentity(@RequestBody IdentityRequest request, Authentication actor) {
    require(actor, "user:manage");
    String username = Validator.username(request.username());
    validateRoles(request.roles());
    if (mongoTemplate.exists(
        Query.query(Criteria.where("usernameLower").is(username.toLowerCase(Locale.ROOT))),
        "users")) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "An account already exists for this email");
    }
    Document user =
        new Document("username", username)
            .append("usernameLower", username.toLowerCase(Locale.ROOT))
            .append("roles", request.roles().stream().distinct().sorted().toList())
            .append("isActive", true)
            .append("createdAt", Date.from(Instant.now()))
            .append("updatedAt", Date.from(Instant.now()))
            .append("lastLoginAt", null);
    mongoTemplate.insert(user, "users");
    return user;
  }

  /**
   * Assigns roles to a user identity by user identifier.
   *
   * @param id user object id
   * @param request role assignment request
   * @param actor authenticated user performing the action
   */
  @PutMapping("/users/identity/{id}/roles")
  public void assignIdentityRoles(
      @PathVariable ObjectId id, @RequestBody RoleAssignment request, Authentication actor) {
    require(actor, "user:manage");
    validateRoles(request.roles());
    Document user = mongoTemplate.findById(id, Document.class, "users");
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
    if (user.getString("username").equalsIgnoreCase(identity(actor))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own roles");
    }
    List<String> currentRoles = user.getList("roles", String.class);
    if (currentRoles.contains("admin") && !request.roles().contains("admin")) {
      long activeAdmins =
          mongoTemplate.count(
              Query.query(Criteria.where("isActive").is(true).and("roles").is("admin")), "users");
      if (activeAdmins <= 1) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Cannot remove the last active admin");
      }
    }
    user.put("roles", request.roles().stream().distinct().sorted().toList());
    user.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(user, "users");
    writeAudit(
        actor,
        "user.roleChange",
        "user",
        id,
        new Document("roles", currentRoles).append("after", request.roles()));
  }

  /**
   * Assigns roles to a user by user identifier.
   *
   * @param id user object id
   * @param request role assignment request
   * @param actor authenticated user performing the action
   */
  @PutMapping("/users/{id}/roles")
  public void assignRoles(
      @PathVariable ObjectId id, @RequestBody RoleAssignment request, Authentication actor) {
    require(actor, "user:manage");
    validateRoles(request.roles());
    Document user = mongoTemplate.findById(id, Document.class, "users");
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
    if (user.getString("username").equalsIgnoreCase(identity(actor))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own roles");
    }
    List<String> currentRoles = user.getList("roles", String.class);
    if (currentRoles.contains("admin") && !request.roles().contains("admin")) {
      long activeAdmins =
          mongoTemplate.count(
              Query.query(Criteria.where("isActive").is(true).and("roles").is("admin")), "users");
      if (activeAdmins <= 1) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Cannot remove the last active admin");
      }
    }
    user.put("roles", request.roles().stream().distinct().sorted().toList());
    user.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(user, "users");
    writeAudit(
        actor,
        "user.roleChange",
        "user",
        id,
        new Document("roles", currentRoles).append("after", request.roles()));
  }

  /**
   * Updates the active status of a user.
   *
   * @param id user object id
   * @param request user status update request
   * @param actor authenticated user performing the action
   */
  @PutMapping("/users/{id}")
  public void setUserActive(
      @PathVariable ObjectId id, @RequestBody UserStatus request, Authentication actor) {
    require(actor, "user:manage");
    Document user = mongoTemplate.findById(id, Document.class, "users");
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
    if (user.getString("username").equalsIgnoreCase(identity(actor)) && !request.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate yourself");
    }
    if (user.getBoolean("isActive", false)
        && !request.isActive()
        && user.getList("roles", String.class).contains("admin")) {
      long activeAdmins =
          mongoTemplate.count(
              Query.query(Criteria.where("isActive").is(true).and("roles").is("admin")), "users");
      if (activeAdmins <= 1) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Cannot deactivate the last active admin");
      }
    }
    user.put("isActive", request.isActive());
    user.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(user, "users");
  }

  private void validateRoles(List<String> roles) {
    List<String> validatedRoles = Validator.roles(roles);
    long count =
        mongoTemplate.count(Query.query(Criteria.where("name").in(validatedRoles)), "roles");
    if (count != validatedRoles.size()) {
      throw new com.kitchensink.validation.ValidationException(
          "roles", "contains unknown role names");
    }
  }

  private void require(Authentication actor, String permission) {
    if (actor == null
        || actor.getAuthorities().stream().noneMatch(a -> permission.equals(a.getAuthority()))) {
      throw new AccessDeniedException("You do not have permission");
    }
  }

  private String identity(Authentication actor) {
    if (actor
        instanceof
        org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauth) {
      String email = oauth.getPrincipal().getAttribute("email");
      if (email != null) {
        return email;
      }
    }
    if (actor
        instanceof
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
                jwt) {
      String email = jwt.getToken().getClaimAsString("email");
      if (email != null) {
        return email;
      }
    }
    return actor.getName();
  }

  private void writeAudit(
      Authentication actor, String action, String entityType, ObjectId entityId, Document changes) {
    Document actorUser =
        mongoTemplate.findOne(
            Query.query(
                Criteria.where("usernameLower").is(actor.getName().toLowerCase(Locale.ROOT))),
            Document.class,
            "users");
    if (actorUser == null) {
      return;
    }
    AuditLog audit = new AuditLog();
    audit.setAt(Instant.now());
    audit.setActorId(actorUser.getObjectId("_id"));
    audit.setAction(action);
    audit.setEntityType(entityType);
    audit.setEntityId(entityId);
    audit.setChanges(changes);
    audit.setRequestId(UUID.randomUUID().toString());
    audit.validateForInsert();
    mongoTemplate.insert(audit);
  }

  /** Request representation for creating or updating a role. */
  public record RoleRequest(String name, String description, List<String> permissions) {}

  /** Request representation for creating a new user identity. */
  public record IdentityRequest(String username, List<String> roles) {}

  /** Request representation for assigning roles to a user. */
  public record RoleAssignment(List<String> roles) {}

  /** Request representation for modifying user active status. */
  public record UserStatus(boolean isActive) {}
}
