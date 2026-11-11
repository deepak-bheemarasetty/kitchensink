package com.kitchensink.controller;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the authenticated user's effective permissions for the client UI.
 *
 * @author Deepak Bheemarasetty
 */
@RestController
public class CurrentUserController {
  private final IdentityResolver identityResolver;
  private final RolePermissionService rolePermissionService;

  public CurrentUserController(
      IdentityResolver identityResolver, RolePermissionService rolePermissionService) {
    this.identityResolver = identityResolver;
    this.rolePermissionService = rolePermissionService;
  }

  /**
   * Retrieves the current authenticated user and their effective permissions.
   *
   * @param authentication current authentication
   * @return current user response
   */
  @GetMapping("/api/v1/me")
  public CurrentUserResponse currentUser(Authentication authentication) {
    String username = identityResolver.resolveIdentity(authentication);
    RolePermissionService.UserPermissions user = rolePermissionService.user(username);
    List<String> permissions =
        user.active()
            ? user.permissions().stream()
                .filter(value -> value.startsWith("member:") || value.endsWith(":manage"))
                .sorted()
                .toList()
            : List.of();
    return new CurrentUserResponse(username, permissions);
  }

  /** Response representation for the current authenticated user and permissions. */
  public record CurrentUserResponse(String username, List<String> permissions) {}
}
