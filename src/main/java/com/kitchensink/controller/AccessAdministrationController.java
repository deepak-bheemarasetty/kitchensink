package com.kitchensink.controller;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.kitchensink.model.AuditLog;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Administrative APIs for application roles and local user accounts. */
@RestController
@RequestMapping("/rest")
public class AccessAdministrationController {
  private static final Set<String> PERMISSIONS = Set.of(
      "member:view", "member:create", "member:update", "member:delete", "user:manage", "role:manage");
  private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,31}$");
  private final MongoTemplate mongoTemplate;
  private final PasswordEncoder passwordEncoder;

  public AccessAdministrationController(MongoTemplate mongoTemplate, PasswordEncoder passwordEncoder) {
    this.mongoTemplate = mongoTemplate;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/roles")
  public List<Document> roles() { return mongoTemplate.findAll(Document.class, "roles"); }

  @PostMapping("/roles")
  public Document createRole(@RequestBody RoleRequest request, Authentication actor) {
    require(actor, "role:manage");
    if (request.name() == null || !request.name().matches("[a-z0-9-]{2,30}")
        || request.description() == null || request.description().length() > 200) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid role name or description");
    }
    String name = request.name().trim().toLowerCase(Locale.ROOT);
    validateRole(request.permissions());
    Document role = new Document("name", name).append("description", request.description().trim())
        .append("permissions", request.permissions().stream().distinct().sorted().toList())
        .append("isSystem", false).append("updatedAt", Date.from(Instant.now()));
    mongoTemplate.insert(role, "roles");
    writeAudit(actor, "role.update", "role", role.getObjectId("_id"), new Document("created", true)
        .append("permissions", request.permissions()));
    return role;
  }

  @PutMapping("/roles/{name}")
  public Document updateRole(@PathVariable String name, @RequestBody RoleRequest request, Authentication actor) {
    require(actor, "role:manage");
    validateRole(request.permissions());
    Query query = Query.query(Criteria.where("name").is(name));
    Document current = mongoTemplate.findOne(query, Document.class, "roles");
    if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
    current.put("description", request.description().trim());
    current.put("permissions", request.permissions().stream().distinct().sorted().toList());
    current.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(current, "roles");
    writeAudit(actor, "role.update", "role", current.getObjectId("_id"),
        new Document("permissions", request.permissions()));
    return current;
  }

  @DeleteMapping("/roles/{name}")
  public void deleteRole(@PathVariable String name, Authentication actor) {
    require(actor, "role:manage");
    Document role = mongoTemplate.findOne(Query.query(Criteria.where("name").is(name)), Document.class, "roles");
    if (role == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
    if (role.getBoolean("isSystem", false)) throw new ResponseStatusException(HttpStatus.CONFLICT, "System roles cannot be deleted");
    long assigned = mongoTemplate.count(Query.query(Criteria.where("roles").is(name)), "users");
    if (assigned > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Role is assigned to " + assigned + " users");
    mongoTemplate.remove(role, "roles");
    writeAudit(actor, "role.update", "role", role.getObjectId("_id"), new Document("deleted", true));
  }

  @GetMapping("/users")
  public List<Document> users(Authentication actor) {
    require(actor, "user:manage");
    Query query = new Query();
    query.fields().exclude("passwordHash");
    return mongoTemplate.find(query, Document.class, "users");
  }

  @PostMapping("/users")
  public Document createUser(@RequestBody UserRequest request, Authentication actor) {
    require(actor, "user:manage");
    if (!USERNAME.matcher(request.username()).matches() || request.password().length() < 12
        || request.password().length() > 128 || request.password().toLowerCase(Locale.ROOT).contains(request.username().toLowerCase(Locale.ROOT))) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid username or password");
    }
    validateRoles(request.roles());
    String username = request.username().trim();
    Document user = new Document("username", username).append("usernameLower", username.toLowerCase(Locale.ROOT))
        .append("passwordHash", passwordEncoder.encode(request.password())).append("roles", request.roles().stream().distinct().sorted().toList())
        .append("isActive", true).append("createdAt", Date.from(Instant.now())).append("updatedAt", Date.from(Instant.now())).append("lastLoginAt", null);
    mongoTemplate.insert(user, "users");
    user.remove("passwordHash");
    return user;
  }

  @PutMapping("/users/{id}/roles")
  public void assignRoles(@PathVariable ObjectId id, @RequestBody RoleAssignment request, Authentication actor) {
    require(actor, "user:manage");
    validateRoles(request.roles());
    Document user = mongoTemplate.findById(id, Document.class, "users");
    if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    if (user.getString("username").equalsIgnoreCase(actor.getName())) throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own roles");
    List<String> currentRoles = user.getList("roles", String.class);
    if (currentRoles.contains("admin") && !request.roles().contains("admin")) {
      long activeAdmins = mongoTemplate.count(
          Query.query(Criteria.where("isActive").is(true).and("roles").is("admin")), "users");
      if (activeAdmins <= 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last active admin");
    }
    user.put("roles", request.roles().stream().distinct().sorted().toList());
    user.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(user, "users");
    writeAudit(actor, "user.roleChange", "user", id, new Document("roles", currentRoles).append("after", request.roles()));
  }

  @PutMapping("/users/{id}")
  public void setUserActive(@PathVariable ObjectId id, @RequestBody UserStatus request, Authentication actor) {
    require(actor, "user:manage");
    Document user = mongoTemplate.findById(id, Document.class, "users");
    if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    if (user.getString("username").equalsIgnoreCase(actor.getName()) && !request.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate yourself");
    }
    if (user.getBoolean("isActive", false) && !request.isActive()
        && user.getList("roles", String.class).contains("admin")) {
      long activeAdmins = mongoTemplate.count(
          Query.query(Criteria.where("isActive").is(true).and("roles").is("admin")), "users");
      if (activeAdmins <= 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot deactivate the last active admin");
    }
    user.put("isActive", request.isActive());
    user.put("updatedAt", Date.from(Instant.now()));
    mongoTemplate.save(user, "users");
  }

  private void validateRole(List<String> permissions) {
    if (permissions == null || permissions.isEmpty() || !PERMISSIONS.containsAll(permissions)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid permissions");
    if (permissions.stream().anyMatch(p -> Set.of("member:create", "member:update", "member:delete").contains(p)) && !permissions.contains("member:view")) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Member write permissions require member:view");
  }

  private void validateRoles(List<String> roles) {
    if (roles == null || roles.isEmpty() || roles.size() > 10 || roles.stream().distinct().count() != roles.size()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "User must have 1 to 10 unique roles");
    long count = mongoTemplate.count(Query.query(Criteria.where("name").in(roles)), "roles");
    if (count != roles.size()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "One or more roles do not exist");
  }

  private void require(Authentication actor, String permission) {
    if (actor == null || actor.getAuthorities().stream().noneMatch(a -> permission.equals(a.getAuthority()))) throw new AccessDeniedException("You do not have permission");
  }

  private void writeAudit(Authentication actor, String action, String entityType, ObjectId entityId, Document changes) {
    Document actorUser = mongoTemplate.findOne(
        Query.query(Criteria.where("usernameLower").is(actor.getName().toLowerCase(Locale.ROOT))),
        Document.class, "users");
    if (actorUser == null) return;
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

  public record RoleRequest(String name, String description, List<String> permissions) {}
  public record UserRequest(String username, String password, List<String> roles) {}
  public record RoleAssignment(List<String> roles) {}
  public record UserStatus(boolean isActive) {}
}
