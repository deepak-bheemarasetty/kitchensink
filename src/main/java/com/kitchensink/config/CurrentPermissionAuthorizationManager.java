package com.kitchensink.config;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Rechecks the local account and current role permissions for each browser request. */
@Component
public class CurrentPermissionAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  private final RolePermissionService permissionService;
  private final IdentityResolver identityResolver;

  public CurrentPermissionAuthorizationManager(
      RolePermissionService permissionService, IdentityResolver identityResolver) {
    this.permissionService = permissionService;
    this.identityResolver = identityResolver;
  }

  @Override
  public AuthorizationResult authorize(
      java.util.function.Supplier<? extends Authentication> authentication,
      RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    if (current == null || !current.isAuthenticated()) {
      return new AuthorizationDecision(false);
    }
    String identity = identityResolver.resolveIdentity(current);
    RolePermissionService.UserPermissions user = permissionService.user(identity);
    String path =
        context
            .getRequest()
            .getRequestURI()
            .substring(context.getRequest().getContextPath().length());
    String required;
    if (path.equals("/") || path.startsWith("/members")) {
      required = "member:view";
    } else if (path.startsWith("/users")) {
      required = "user:manage";
    } else if (path.startsWith("/roles")) {
      required = "role:manage";
    } else {
      // Deny by default for any unspecified path protected by this manager
      return new AuthorizationDecision(false);
    }
    return new AuthorizationDecision(user.active() && user.permissions().contains(required));
  }
}
