package com.kitchensink.controller;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns the authenticated user's effective permissions for the client UI. */
@RestController
public class CurrentUserController {
  /**
   * Retrieves the current authenticated user and their effective permissions.
   *
   * @param authentication current authentication
   * @return current user response
   */
  @GetMapping("/api/v1/me")
  public CurrentUserResponse currentUser(Authentication authentication) {
    List<String> permissions =
        authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(value -> value.startsWith("member:") || value.endsWith(":manage"))
            .sorted()
            .toList();
    return new CurrentUserResponse(authentication.getName(), permissions);
  }

  /** Response representation for the current authenticated user and permissions. */
  public record CurrentUserResponse(String username, List<String> permissions) {}
}
