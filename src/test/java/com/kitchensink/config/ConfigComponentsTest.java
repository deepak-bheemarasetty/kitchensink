package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Unit tests for filters, data initializers, and authorization managers.
 *
 * @author Deepak Bheemarasetty
 */
class ConfigComponentsTest {

  @Test
  void databaseDataInitializerSeedsRolesAndUsers() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    DatabaseDataInitializer initializer = new DatabaseDataInitializer(mongoTemplate);

    initializer.run(mock(ApplicationArguments.class));

    verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
        .upsert(any(Query.class), any(), eq("roles"));
    verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
        .upsert(any(Query.class), any(), eq("users"));
  }

  @Test
  void requestIdFilterProcessesRequest() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-ID", "custom-request-id-123");
    request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.50");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Request-ID")).isEqualTo("custom-request-id-123");
    verify(chain).doFilter(request, response);
  }

  @Test
  void currentPermissionAuthorizationManagerAuthorize() {
    RolePermissionService permissions = mock(RolePermissionService.class);
    IdentityResolver identityResolver = mock(IdentityResolver.class);
    final CurrentPermissionAuthorizationManager manager =
        new CurrentPermissionAuthorizationManager(permissions, identityResolver);

    TestingAuthenticationToken auth =
        new TestingAuthenticationToken("admin@example.com", "pwd", List.of(() -> "member:view"));
    auth.setAuthenticated(true);
    org.mockito.Mockito.when(identityResolver.resolveIdentity(auth))
        .thenReturn("admin@example.com");
    org.mockito.Mockito.when(permissions.user("admin@example.com"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("member:view")));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kitchensink/members");
    request.setContextPath("/kitchensink");
    RequestAuthorizationContext context = new RequestAuthorizationContext(request);

    AuthorizationDecision decision = (AuthorizationDecision) manager.authorize(() -> auth, context);
    assertThat(decision.isGranted()).isTrue();

    // Check denied path
    MockHttpServletRequest deniedReq = new MockHttpServletRequest("GET", "/kitchensink/users");
    deniedReq.setContextPath("/kitchensink");
    RequestAuthorizationContext deniedContext = new RequestAuthorizationContext(deniedReq);
    AuthorizationDecision deniedDecision =
        (AuthorizationDecision) manager.authorize(() -> auth, deniedContext);
    assertThat(deniedDecision.isGranted()).isFalse();
  }
}
