package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  void databaseSchemaInitializerCreatesCollectionsIndexesAndValidators() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    org.springframework.data.mongodb.core.index.IndexOperations indexOps =
        mock(org.springframework.data.mongodb.core.index.IndexOperations.class);
    org.mockito.Mockito.when(mongoTemplate.indexOps(any(String.class))).thenReturn(indexOps);
    org.mockito.Mockito.when(mongoTemplate.collectionExists(any(String.class))).thenReturn(false);

    DatabaseSchemaInitializer schemaInitializer = new DatabaseSchemaInitializer(mongoTemplate);
    schemaInitializer.run(mock(ApplicationArguments.class));

    verify(mongoTemplate).createCollection("members");
    verify(mongoTemplate).createCollection("users");
    verify(mongoTemplate).createCollection("roles");
    verify(mongoTemplate).createCollection("audit_logs");
    verify(mongoTemplate).createCollection("idempotency_keys");
    verify(mongoTemplate, org.mockito.Mockito.times(4))
        .executeCommand(any(org.bson.Document.class));
    verify(indexOps, org.mockito.Mockito.atLeast(7))
        .createIndex(any(org.springframework.data.mongodb.core.index.IndexDefinition.class));
  }

  @Test
  void databaseSchemaInitializerSkipsExistingCollectionsAndContainsMongoFailures() {
    MongoTemplate existingTemplate = mock(MongoTemplate.class);
    org.mockito.Mockito.when(existingTemplate.indexOps(any(String.class)))
        .thenReturn(mock(org.springframework.data.mongodb.core.index.IndexOperations.class));
    org.mockito.Mockito.when(existingTemplate.collectionExists(any(String.class))).thenReturn(true);
    new DatabaseSchemaInitializer(existingTemplate).run(mock(ApplicationArguments.class));
    verify(existingTemplate, org.mockito.Mockito.never()).createCollection(any(String.class));

    MongoTemplate failingTemplate = mock(MongoTemplate.class);
    org.mockito.Mockito.when(failingTemplate.indexOps(any(String.class)))
        .thenReturn(mock(org.springframework.data.mongodb.core.index.IndexOperations.class));
    org.mockito.Mockito.when(failingTemplate.collectionExists(any(String.class))).thenReturn(false);
    org.mockito.Mockito.when(failingTemplate.executeCommand(any(org.bson.Document.class)))
        .thenThrow(new IllegalStateException("Mongo unavailable"));
    new DatabaseSchemaInitializer(failingTemplate).run(mock(ApplicationArguments.class));
  }

  @Test
  void databaseDataInitializerSeedsRolesAndUsers() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    DatabaseDataInitializer initializer =
        new DatabaseDataInitializer(mongoTemplate, java.util.List.of("admin@example.com"));

    initializer.run(mock(ApplicationArguments.class));

    verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
        .upsert(any(Query.class), any(), eq("roles"));
    verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
        .upsert(any(Query.class), any(), eq("users"));
  }

  @Test
  void databaseDataInitializerSkipsBlankAdminEmailsAndHandlesNoAdmins() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    DatabaseDataInitializer initializer =
        new DatabaseDataInitializer(
            mongoTemplate,
            java.util.Arrays.asList(null, " ", " ADMIN@example.com ", "other@example.com"));
    initializer.run(mock(ApplicationArguments.class));

    verify(mongoTemplate, org.mockito.Mockito.times(2))
        .upsert(any(Query.class), any(), eq("users"));

    MongoTemplate noAdminsTemplate = mock(MongoTemplate.class);
    new DatabaseDataInitializer(noAdminsTemplate, null).run(mock(ApplicationArguments.class));
    verify(noAdminsTemplate, org.mockito.Mockito.never())
        .upsert(any(Query.class), any(), eq("users"));
  }

  @Test
  void requestIdFilterProcessesRequest() throws Exception {
    final RequestIdFilter filter = new RequestIdFilter();
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
  void requestIdFilterReplacesUnsafeIdsAndUsesRemoteAddressFallback() throws Exception {
    final RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-ID", "invalid id");
    request.addHeader("X-Forwarded-For", " ");
    request.setRemoteAddr("192.0.2.10");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> assertThat(org.slf4j.MDC.get("clientIp")).isEqualTo("192.0.2.10");

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Request-ID")).matches("[0-9a-f-]{36}");
    assertThat(org.slf4j.MDC.get("requestId")).isNull();
    assertThat(org.slf4j.MDC.get("clientIp")).isNull();
  }

  @Test
  void requestIdFilterFallsBackForLongForwardedAndRemoteAddresses() throws Exception {
    final RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-ID", "safe-id_123");
    request.addHeader("X-Forwarded-For", "1.1.1.1, " + "x".repeat(65));
    request.setRemoteAddr("r".repeat(65));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-ID")).isEqualTo("safe-id_123");
  }

  @Test
  void requestIdFilterUsesRemoteAddressWhenForwardedAddressIsEmptyOrUnavailable() throws Exception {
    final RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", ", ");
    request.setRemoteAddr("198.51.100.8");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> assertThat(org.slf4j.MDC.get("clientIp")).isEqualTo("198.51.100.8");

    filter.doFilter(request, response, chain);

    MockHttpServletRequest noRemoteAddress = new MockHttpServletRequest();
    noRemoteAddress.setRemoteAddr(null);
    noRemoteAddress.addHeader("X-Forwarded-For", ", ");
    filter.doFilter(
        noRemoteAddress,
        new MockHttpServletResponse(),
        (req, res) -> assertThat(org.slf4j.MDC.get("clientIp")).isEqualTo("unknown"));
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

  @Test
  void currentPermissionAuthorizationManagerChecksAllSupportedPathsAndAuthentication() {
    RolePermissionService permissions = mock(RolePermissionService.class);
    IdentityResolver identityResolver = mock(IdentityResolver.class);
    final CurrentPermissionAuthorizationManager manager =
        new CurrentPermissionAuthorizationManager(permissions, identityResolver);
    TestingAuthenticationToken auth = new TestingAuthenticationToken("admin@example.com", "pwd");
    auth.setAuthenticated(true);
    when(identityResolver.resolveIdentity(auth)).thenReturn("admin@example.com");
    when(permissions.user("admin@example.com"))
        .thenReturn(
            new RolePermissionService.UserPermissions(
                true, Set.of("member:view", "user:manage", "role:manage")));

    assertThat(decision(manager, auth, "/").isGranted()).isTrue();
    assertThat(decision(manager, auth, "/users/accounts").isGranted()).isTrue();
    assertThat(decision(manager, auth, "/roles/admin").isGranted()).isTrue();
    assertThat(decision(manager, auth, "/unmapped").isGranted()).isFalse();

    auth.setAuthenticated(false);
    assertThat(decision(manager, auth, "/members").isGranted()).isFalse();
    assertThat(decision(manager, null, "/members").isGranted()).isFalse();
  }

  private static AuthorizationDecision decision(
      CurrentPermissionAuthorizationManager manager,
      TestingAuthenticationToken authentication,
      String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app" + path);
    request.setContextPath("/app");
    return (AuthorizationDecision)
        manager.authorize(() -> authentication, new RequestAuthorizationContext(request));
  }
}
