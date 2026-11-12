package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.controller.AccessAdministrationController.IdentityRequest;
import com.kitchensink.controller.AccessAdministrationController.RoleAssignment;
import com.kitchensink.controller.AccessAdministrationController.RoleRequest;
import com.kitchensink.controller.AccessAdministrationController.UserStatus;
import com.kitchensink.model.AuditLog;
import com.kitchensink.service.IdentityResolver;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
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
  void updateRoleAndDeleteRoleRejectMissingAndAssignedRoles() {
    RoleRequest request = new RoleRequest("custom", "description", List.of("member:view"));
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("roles"))).thenReturn(null);
    assertThatThrownBy(() -> controller.updateRole("custom", request, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Role not found");
    assertThatThrownBy(() -> controller.deleteRole("custom", adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Role not found");

    Document assignedRole =
        new Document("_id", new ObjectId()).append("name", "custom").append("isSystem", false);
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("roles")))
        .thenReturn(assignedRole);
    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(1L);
    assertThatThrownBy(() -> controller.deleteRole("custom", adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("currently assigned");
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
  void auditCapturesSafeMdcValuesAndOmitsInvalidClientIp() {
    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    try {
      MDC.put("requestId", "trace-id");
      MDC.put("clientIp", "192.0.2.1");
      controller.createRole(
          new RoleRequest("first-role", "description", List.of("member:view")), adminAuth);

      MDC.put("requestId", " ");
      MDC.put("clientIp", "x".repeat(65));
      controller.createRole(
          new RoleRequest("second-role", "description", List.of("member:view")), adminAuth);
    } finally {
      MDC.remove("requestId");
      MDC.remove("clientIp");
    }

    verify(mongoTemplate, times(2)).insert(auditCaptor.capture());
    List<AuditLog> audits = auditCaptor.getAllValues();
    assertThat(audits.get(0).getRequestId()).isEqualTo("trace-id");
    assertThat(audits.get(0).getIp()).isEqualTo("192.0.2.1");
    assertThat(audits.get(1).getRequestId()).matches("[0-9a-f-]{36}");
    assertThat(audits.get(1).getIp()).isNull();
  }

  @Test
  void usersMarkSelfAndHandleMissingIdAndIdentityRegistrationConflicts() {
    Document self = new Document("username", "ADMIN@example.com").append("roles", List.of("admin"));
    Document noId = new Document("username", "target@example.com").append("roles", List.of());
    when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
        .thenReturn(List.of(self, noId));

    List<Document> users = controller.users(adminAuth);
    assertThat(users.get(0).getBoolean("isSelf")).isTrue();
    assertThat(users.get(1).getBoolean("isSelf")).isFalse();
    assertThat(users.get(1).containsKey("id")).isFalse();

    when(mongoTemplate.count(any(Query.class), eq("roles"))).thenReturn(0L);
    assertThatThrownBy(
            () ->
                controller.registerIdentity(
                    new IdentityRequest("new@example.com", List.of("unknown")), adminAuth))
        .isInstanceOf(com.kitchensink.validation.ValidationException.class);

    when(mongoTemplate.count(any(Query.class), eq("roles"))).thenReturn(1L);
    when(mongoTemplate.exists(any(Query.class), eq("users"))).thenReturn(true);
    assertThatThrownBy(
            () ->
                controller.registerIdentity(
                    new IdentityRequest("new@example.com", List.of("viewer")), adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already exists");
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
  void assignRolesRejectsMissingSelfAndLastAdministratorUpdates() {
    ObjectId uid = new ObjectId();
    RoleAssignment removeAdmin = new RoleAssignment(List.of("viewer"));
    when(mongoTemplate.count(any(Query.class), eq("roles"))).thenReturn(1L);
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(null);
    assertThatThrownBy(() -> controller.assignRoles(uid, removeAdmin, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("User not found");

    Document self = new Document("username", "admin@example.com").append("roles", List.of("admin"));
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(self);
    assertThatThrownBy(() -> controller.assignRoles(uid, removeAdmin, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("cannot change your own roles");

    Document otherAdmin =
        new Document("username", "other@example.com").append("roles", List.of("admin"));
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(otherAdmin);
    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(1L);
    assertThatThrownBy(() -> controller.assignRoles(uid, removeAdmin, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last active admin");

    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(2L, 0L);
    assertThatThrownBy(() -> controller.assignRoles(uid, removeAdmin, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last active admin");
    verify(mongoTemplate, org.mockito.Mockito.times(2))
        .updateFirst(
            any(Query.class),
            any(org.springframework.data.mongodb.core.query.Update.class),
            eq("users"));
  }

  @Test
  void userStatusRejectsMissingSelfAndLastAdministratorDeactivation() {
    ObjectId uid = new ObjectId();
    UserStatus deactivate = new UserStatus(false);
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(null);
    assertThatThrownBy(() -> controller.setUserActive(uid, deactivate, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("User not found");

    Document self =
        new Document("username", "admin@example.com")
            .append("roles", List.of("admin"))
            .append("isActive", true);
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(self);
    assertThatThrownBy(() -> controller.setUserActive(uid, deactivate, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("cannot deactivate yourself");

    Document otherAdmin =
        new Document("username", "other@example.com")
            .append("roles", List.of("admin"))
            .append("isActive", true);
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(otherAdmin);
    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(1L);
    assertThatThrownBy(() -> controller.setUserActive(uid, deactivate, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last active admin");

    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(2L, 0L);
    assertThatThrownBy(() -> controller.setUserActive(uid, deactivate, adminAuth))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last active admin");
    verify(mongoTemplate, org.mockito.Mockito.times(2))
        .updateFirst(
            any(Query.class),
            any(org.springframework.data.mongodb.core.query.Update.class),
            eq("users"));
  }

  @Test
  void lastAdministratorUpdatesSucceedWhenAnotherAdminRemains() {
    ObjectId uid = new ObjectId();
    Document otherAdmin =
        new Document("username", "other@example.com")
            .append("roles", List.of("admin"))
            .append("isActive", true);
    when(mongoTemplate.findById(eq(uid), eq(Document.class), eq("users"))).thenReturn(otherAdmin);
    when(mongoTemplate.count(any(Query.class), eq("roles"))).thenReturn(1L);
    when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(2L, 1L, 2L, 1L);

    controller.assignRoles(uid, new RoleAssignment(List.of("viewer")), adminAuth);
    controller.setUserActive(uid, new UserStatus(false), adminAuth);
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
