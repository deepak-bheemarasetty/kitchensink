package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import java.util.List;
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
}
