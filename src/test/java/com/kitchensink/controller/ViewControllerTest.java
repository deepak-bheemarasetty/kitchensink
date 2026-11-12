package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit tests for LoginController and MemberViewController.
 *
 * @author Deepak Bheemarasetty
 */
class ViewControllerTest {

  @Test
  void loginControllerViewName() {
    LoginController controller = new LoginController();
    assertThat(controller.showLogin()).isEqualTo("login");
  }

  @Test
  void memberViewControllerHomeAndMembers() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IdentityResolver identityResolver = mock(IdentityResolver.class);
    RolePermissionService rolePermissionService = mock(RolePermissionService.class);
    when(identityResolver.resolveIdentity(any())).thenReturn("admin");
    when(rolePermissionService.user("admin"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("member:view")));

    MemberViewController controller =
        new MemberViewController(mongoTemplate, identityResolver, rolePermissionService);
    Model model = new ConcurrentModel();

    Authentication auth =
        new TestingAuthenticationToken("admin", "pwd", List.of(() -> "member:view"));
    assertThat(controller.home(auth)).isEqualTo("redirect:/members");
    assertThat(controller.members(auth, model)).isEqualTo("all-members");
  }

  @Test
  void memberViewsEnforcePermissionsAndPopulateFilteredShellData() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IdentityResolver identityResolver = mock(IdentityResolver.class);
    RolePermissionService permissions = mock(RolePermissionService.class);
    MemberViewController controller =
        new MemberViewController(mongoTemplate, identityResolver, permissions);
    when(identityResolver.resolveIdentity(any()))
        .thenAnswer(
            invocation -> {
              Authentication authentication = invocation.getArgument(0);
              return authentication.getName();
            });
    when(permissions.user("members"))
        .thenReturn(
            new RolePermissionService.UserPermissions(
                true,
                Set.of("member:view", "member:create", "member:update", "member:delete", "extra")));
    when(permissions.user("accounts"))
        .thenReturn(
            new RolePermissionService.UserPermissions(true, Set.of("user:manage", "role:manage")));
    when(permissions.user("roles"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("role:manage")));
    when(permissions.user("members-view"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("member:view")));
    when(permissions.user("inactive"))
        .thenReturn(
            new RolePermissionService.UserPermissions(true, Set.of("member:view")),
            new RolePermissionService.UserPermissions(false, Set.of("member:view")));
    when(permissions.user("blocked"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of()));
    when(mongoTemplate.findAll(Map.class, "roles")).thenReturn(List.of(Map.of("name", "viewer")));

    TestingAuthenticationToken memberAuth = auth("members");
    ConcurrentModel model = new ConcurrentModel();
    assertThat(controller.members(memberAuth, model)).isEqualTo("all-members");
    assertThat(model.getAttribute("permissions"))
        .isEqualTo(List.of("member:create", "member:delete", "member:update", "member:view"));
    assertThat(model.getAttribute("canCreate")).isEqualTo(true);

    assertThat(controller.users(auth("accounts"), new ConcurrentModel())).isEqualTo("users");
    assertThat(controller.roles(auth("roles"), new ConcurrentModel())).isEqualTo("roles");
    assertThat(controller.members(auth("blocked"), new ConcurrentModel())).isEqualTo("forbidden");
    assertThat(controller.users(auth("blocked"), new ConcurrentModel())).isEqualTo("forbidden");
    assertThat(controller.roles(auth("blocked"), new ConcurrentModel())).isEqualTo("forbidden");

    ConcurrentModel inactiveModel = new ConcurrentModel();
    assertThat(controller.members(auth("inactive"), inactiveModel)).isEqualTo("all-members");
    assertThat(inactiveModel.getAttribute("permissions")).isEqualTo(List.of());
    assertThat(controller.home(auth("accounts"))).isEqualTo("redirect:/users");
    assertThat(controller.home(auth("roles"))).isEqualTo("redirect:/roles");
    assertThat(controller.home(auth("blocked"))).isEqualTo("no-access");
    assertThat(controller.noAccess(auth("roles"))).isEqualTo("redirect:/");
    assertThat(controller.noAccess(auth("members-view"))).isEqualTo("redirect:/");
    assertThat(controller.noAccess(auth("accounts"))).isEqualTo("redirect:/");
    assertThat(controller.noAccess(auth("blocked"))).isEqualTo("no-access");
    assertThat(controller.noAccess(null)).isEqualTo("no-access");
    assertThat(controller.forbidden()).isEqualTo("forbidden");
  }

  private static TestingAuthenticationToken auth(String name) {
    return new TestingAuthenticationToken(name, "pwd", List.of());
  }
}
