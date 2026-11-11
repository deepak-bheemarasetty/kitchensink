package com.kitchensink.controller;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the simple, server-rendered member and account workspaces.
 *
 * @author Deepak Bheemarasetty
 */
@Controller
public class MemberViewController {
  private final MongoTemplate mongoTemplate;
  private final IdentityResolver identityResolver;
  private final RolePermissionService rolePermissionService;

  /**
   * Constructs the member view controller with required dependencies.
   *
   * @param mongoTemplate template used for direct database inquiries (e.g. registration codes)
   * @param identityResolver resolver for extracting canonical username
   * @param rolePermissionService service for dynamic authorization and permission lookups
   */
  public MemberViewController(
      MongoTemplate mongoTemplate,
      IdentityResolver identityResolver,
      RolePermissionService rolePermissionService) {
    this.mongoTemplate = mongoTemplate;
    this.identityResolver = identityResolver;
    this.rolePermissionService = rolePermissionService;
  }

  /**
   * Routes the authenticated user to their default landing view.
   *
   * @param authentication current authentication
   * @return redirect view name
   */
  @GetMapping("/")
  public String home(Authentication authentication) {
    if (has(authentication, "member:view")) {
      return "redirect:/members";
    }
    if (has(authentication, "user:manage")) {
      return "redirect:/users";
    }
    if (has(authentication, "role:manage")) {
      return "redirect:/roles";
    }
    return "no-access";
  }

  /**
   * Renders the members management view.
   *
   * @param authentication current authentication
   * @param model template model
   * @return members view name
   */
  @GetMapping("/members")
  public String members(Authentication authentication, Model model) {
    if (!has(authentication, "member:view")) {
      return "forbidden";
    }
    addShell(authentication, model);
    return "all-members";
  }

  /**
   * Renders the user accounts management view.
   *
   * @param authentication current authentication
   * @param model template model
   * @return users view name
   */
  @GetMapping("/users")
  public String users(Authentication authentication, Model model) {
    if (!has(authentication, "user:manage")) {
      return "forbidden";
    }
    addShell(authentication, model);
    List<Map> roles = mongoTemplate.findAll(Map.class, "roles");
    model.addAttribute("roles", roles);
    return "users";
  }

  /**
   * Renders the role permissions management view.
   *
   * @param authentication current authentication
   * @param model template model
   * @return roles view name
   */
  @GetMapping("/roles")
  public String roles(Authentication authentication, Model model) {
    if (!has(authentication, "role:manage")) {
      return "forbidden";
    }
    addShell(authentication, model);
    model.addAttribute("roles", mongoTemplate.findAll(Map.class, "roles"));
    return "roles";
  }

  /**
   * Renders the no-access notice view.
   *
   * @param authentication current authentication
   * @return view name
   */
  @GetMapping("/no-access")
  public String noAccess(Authentication authentication) {
    return has(authentication, "member:view")
            || has(authentication, "user:manage")
            || has(authentication, "role:manage")
        ? "redirect:/"
        : "no-access";
  }

  @GetMapping("/forbidden")
  public String forbidden() {
    return "forbidden";
  }

  private void addShell(Authentication authentication, Model model) {
    String username = identityResolver.resolveIdentity(authentication);
    RolePermissionService.UserPermissions user = rolePermissionService.user(username);
    List<String> permissions =
        user.active()
            ? user.permissions().stream()
                .filter(value -> value.startsWith("member:") || value.endsWith(":manage"))
                .sorted()
                .toList()
            : List.of();
    model.addAttribute("username", username);
    model.addAttribute("permissions", permissions);
    model.addAttribute("canCreate", permissions.contains("member:create"));
    model.addAttribute("canUpdate", permissions.contains("member:update"));
    model.addAttribute("canDelete", permissions.contains("member:delete"));
    model.addAttribute("canManageUsers", permissions.contains("user:manage"));
    model.addAttribute("canManageRoles", permissions.contains("role:manage"));
  }

  private boolean has(Authentication authentication, String permission) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    String username = identityResolver.resolveIdentity(authentication);
    RolePermissionService.UserPermissions user = rolePermissionService.user(username);
    return user.active() && user.permissions().contains(permission);
  }
}
