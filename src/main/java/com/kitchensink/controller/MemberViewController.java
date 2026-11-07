package com.kitchensink.controller;

import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the simple, server-rendered member and account workspaces. */
@Controller
public class MemberViewController {
  private final MongoTemplate mongoTemplate;

  public MemberViewController(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
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
    List<String> permissions =
        authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(value -> value.startsWith("member:") || value.endsWith(":manage"))
            .sorted()
            .toList();
    String username = authentication.getName();
    if (authentication
        instanceof
        org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauth) {
      String email = oauth.getPrincipal().getAttribute("email");
      if (email != null) {
        username = email;
      }
    }
    model.addAttribute("username", username);
    model.addAttribute("permissions", permissions);
    model.addAttribute("canCreate", permissions.contains("member:create"));
    model.addAttribute("canUpdate", permissions.contains("member:update"));
    model.addAttribute("canDelete", permissions.contains("member:delete"));
    model.addAttribute("canManageUsers", permissions.contains("user:manage"));
    model.addAttribute("canManageRoles", permissions.contains("role:manage"));
  }

  private boolean has(Authentication authentication, String permission) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(authority -> permission.equals(authority.getAuthority()));
  }
}
