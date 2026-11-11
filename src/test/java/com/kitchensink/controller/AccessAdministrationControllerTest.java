package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.controller.AccessAdministrationController.IdentityRequest;
import com.kitchensink.controller.AccessAdministrationController.RoleAssignment;
import com.kitchensink.controller.AccessAdministrationController.RoleRequest;
import com.kitchensink.controller.AccessAdministrationController.UserStatus;
import com.kitchensink.service.IdentityResolver;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for AccessAdministrationController.
 *
 * @author Deepak Bheemarasetty
 */
class AccessAdministrationControllerTest {

  private MongoTemplate mongoTemplate;
  private IdentityResolver identityResolver;
  private com.kitchensink.service.RolePermissionService rolePermissionService;
  private AccessAdministrationController controller;
  private TestingAuthenticationToken adminAuth;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    identityResolver = mock(IdentityResolver.class);
    rolePermissionService = mock(com.kitchensink.service.RolePermissionService.class);
    when(rolePermissionService.user(any()))
        .thenReturn(
            new com.kitchensink.service.RolePermissionService.UserPermissions(
                true, Set.of("role:manage", "user:manage")));
    controller =
        new AccessAdministrationController(mongoTemplate, identityResolver, rolePermissionService);

    adminAuth =
        new TestingAuthenticationToken(
            "admin@example.com", "pwd", List.of(() -> "role:manage", () -> "user:manage"));
    when(identityResolver.resolveIdentity(adminAuth)).thenReturn("admin@example.com");
    when(identityResolver.requireActorId(adminAuth)).thenReturn(new ObjectId());
    when(mongoTemplate.insert(any(Document.class), eq("roles")))
        .thenAnswer(
            inv -> {
              Document doc = inv.getArgument(0);
              doc.put("_id", new ObjectId());
              return doc;
            });
    when(mongoTemplate.insert(any(Document.class), eq("users")))
        .thenAnswer(
            inv -> {
              Document doc = inv.getArgument(0);
              doc.put("_id", new ObjectId());
              return doc;
            });
  }

  @Test
  void rolesListAndCreate() {
    Document roleDoc = new Document("name", "custom").append("permissions", List.of("member:view"));
    when(mongoTemplate.findAll(Document.class, "roles")).thenReturn(List.of(roleDoc));

    assertThat(controller.roles()).hasSize(1);

    RoleRequest req = new RoleRequest("custom", "desc", List.of("member:view"));
    Document created = controller.createRole(req, adminAuth);
    assertThat(created.getString("name")).isEqualTo("custom");
  }

  @Test
  void updateRole() {
    ObjectId id = new ObjectId();
    Document roleDoc =
        new Document("_id", id)
            .append("name", "custom")
            .append("permissions", List.of("member:view"));
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("roles")))
        .thenReturn(roleDoc);

    RoleRequest req =
        new RoleRequest("custom", "new desc", List.of("member:view", "member:create"));
    Document updated = controller.updateRole("custom", req, adminAuth);
    assertThat(updated.getString("description")).isEqualTo("new desc");

    roleDoc.put("isSystem", true);
    assertThatThrownBy(() -> controller.updateRole("custom", req, adminAuth))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void deleteRole() {
    Document roleDoc =
        new Document("_id", new ObjectId()).append("name", "custom").append("isSystem", false);
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("roles")))
        .thenReturn(roleDoc);
    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(0L);

    controller.deleteRole("custom", adminAuth);

    roleDoc.put("isSystem", true);
    assertThatThrownBy(() -> controller.deleteRole("custom", adminAuth))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void usersListAndRegisterIdentity() {
    ObjectId uid = new ObjectId();
    Document userDoc =
        new Document("_id", uid)
            .append("username", "user@example.com")
            .append("usernameLower", "user@example.com")
            .append("roles", List.of("viewer"));

    when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
        .thenReturn(List.of(userDoc));

    List<Document> list = controller.users(adminAuth);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getString("id")).isEqualTo(uid.toHexString());

    when(mongoTemplate.count(any(Query.class), eq("roles"))).thenReturn(1L);
    when(mongoTemplate.exists(any(Query.class), eq("users"))).thenReturn(false);

    IdentityRequest req = new IdentityRequest("newuser@example.com", List.of("viewer"));
    Document created = controller.registerIdentity(req, adminAuth);
    assertThat(created.getString("username")).isEqualTo("newuser@example.com");
  }

  @Test
  void assignRolesAndStatus() {
    ObjectId uid = new ObjectId();
    Document userDoc =
        new Document("_id", uid)
            .append("username", "target@example.com")
            .append("roles", List.of("viewer"))
            .append("isActive", true);

    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(userDoc);
    when(mongoTemplate.count(any(Query.class), eq("roles"))).thenReturn(1L);

    RoleAssignment assign = new RoleAssignment(List.of("viewer"));
    controller.assignRoles(uid, assign, adminAuth);
    controller.assignIdentityRoles(uid, assign, adminAuth);

    UserStatus status = new UserStatus(false);
    controller.setUserActive(uid, status, adminAuth);
  }

  @Test
  void permissionGuardChecks() {
    TestingAuthenticationToken noPerms = new TestingAuthenticationToken("user", "pwd", List.of());
    when(identityResolver.resolveIdentity(noPerms)).thenReturn("user");
    when(rolePermissionService.user("user"))
        .thenReturn(
            new com.kitchensink.service.RolePermissionService.UserPermissions(
                false, Collections.emptySet()));
    assertThatThrownBy(
            () ->
                controller.createRole(
                    new RoleRequest("valid-role", "desc", List.of("member:view")), noPerms))
        .isInstanceOf(AccessDeniedException.class);
  }
}
