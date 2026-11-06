package com.kitchensink.config;

import com.kitchensink.service.RolePermissionService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Rechecks the local account and current role permissions for each browser request. */
@Component
public class CurrentPermissionAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  private final RolePermissionService permissionService;

  public CurrentPermissionAuthorizationManager(RolePermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Override
  public AuthorizationResult authorize(
      java.util.function.Supplier<? extends Authentication> authentication,
      RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    if (current == null || !current.isAuthenticated()) return new AuthorizationDecision(false);
    String username = current.getName();
    if (current instanceof OAuth2AuthenticationToken oauth) {
      String email = oauth.getPrincipal().getAttribute("email");
      if (email != null) username = email;
    } else if (current instanceof JwtAuthenticationToken jwt) {
      String email = jwt.getToken().getClaimAsString("email");
      if (email != null) username = email;
    }
    RolePermissionService.UserPermissions user = permissionService.user(username);
    return new AuthorizationDecision(user.active() && user.permissions().contains("member:view"));
  }
}
