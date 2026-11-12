package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Execution;
import com.kitchensink.service.ExecutionService;
import com.kitchensink.service.IdentityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for ExecutionTrackingFilter.
 *
 * @author Deepak Bheemarasetty
 */
class ExecutionTrackingFilterTest {

  private ExecutionService executionService;
  private IdentityResolver identityResolver;
  private ExecutionTrackingFilter filter;

  @BeforeEach
  void setUp() {
    executionService = mock(ExecutionService.class);
    identityResolver = mock(IdentityResolver.class);
    filter = new ExecutionTrackingFilter(executionService, identityResolver);
    SecurityContextHolder.clearContext();
  }

  @Test
  void nonApiPathIsIgnored() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kitchensink/members");
    request.setContextPath("/kitchensink");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
  }

  @Test
  void contextPathIsIgnoredWhenItDoesNotPrefixRequestUri() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
    request.setContextPath("/other");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
  }

  @Test
  void unauthenticatedRequestIsIgnored() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
  }

  @Test
  void anonymousUserIsIgnored() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    Authentication auth = new UsernamePasswordAuthenticationToken("anonymousUser", null);
    SecurityContextHolder.getContext().setAuthentication(auth);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
  }

  @Test
  void authenticatedAnonymousPrincipalIsIgnored() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "anonymousUser", null, java.util.List.of()));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
  }

  @Test
  void emptyApiPathIsNormalizedAndNullExecutionIsNotCompleted()
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
    request.setContextPath("/api");
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated("user@example.com", "pass", List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(204));

    assertThat(response.getStatus()).isEqualTo(204);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
    verify(executionService, never()).recordCompletion(any(), any(), any());

    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");
    when(executionService.recordReceived("user@example.com", "user.me", "GET", null))
        .thenReturn(null);
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/me"),
        new MockHttpServletResponse(),
        (req, res) -> {});
    verify(executionService).recordReceived("user@example.com", "user.me", "GET", null);
    verify(executionService, never()).recordCompletion(any(), any(), any());
  }

  @Test
  void identityResolutionFailureIsIgnored() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenThrow(new RuntimeException("Lookup failed"));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(executionService, never()).recordReceived(any(), any(), any(), any());
  }

  @Test
  void authenticatedSuccessfulRequestRecordsSuccess() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/members");
    request.addHeader("X-Request-ID", "req-test-123");

    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(
            "user@example.com", "member.create", "POST", "req-test-123"))
        .thenReturn(execution);

    FilterChain chain =
        (req, res) -> {
          res.getOutputStream().write("{\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8));
          ((HttpServletResponse) res).setStatus(201);
        };

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);

    verify(executionService).recordCompletion(execId, "SUCCESS", null);
    assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"123\"}");
  }

  @Test
  void requestIdFromMdcTakesPrecedenceOverHeader() throws ServletException, IOException {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated("user@example.com", "pass", List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");
    Execution execution = new Execution();
    execution.setId(new ObjectId());
    when(executionService.recordReceived("user@example.com", "user.me", "GET", "mdc-id"))
        .thenReturn(execution);
    MDC.put("requestId", "mdc-id");
    try {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
      request.addHeader("X-Request-ID", "header-id");
      filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    } finally {
      MDC.remove("requestId");
    }
    verify(executionService)
        .recordReceived("user@example.com", "user.me", "GET", "mdc-id");
  }

  @Test
  void authenticatedFailedRequestRecordsFailureWithErrorMessage()
      throws ServletException, IOException {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/kitchensink/api/v1/members/123");
    request.setContextPath("/kitchensink");

    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(
            eq("user@example.com"), eq("member.get-by-id"), eq("GET"), any()))
        .thenReturn(execution);

    FilterChain chain =
        (req, res) -> {
          String errorBody =
              "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"Member not found\"}}";
          res.getOutputStream().write(errorBody.getBytes(StandardCharsets.UTF_8));
          ((HttpServletResponse) res).setStatus(404);
        };

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);

    verify(executionService).recordCompletion(execId, "FAILED", "Member not found");
  }

  @Test
  void authenticatedFailedRequestWithFallbackErrorMessage() throws ServletException, IOException {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(
            eq("user@example.com"), eq("role.delete"), eq("DELETE"), any()))
        .thenReturn(execution);

    FilterChain chain =
        (req, res) -> {
          String errorBody = "{\"message\":\"Direct error message\"}";
          res.getOutputStream().write(errorBody.getBytes(StandardCharsets.UTF_8));
          ((HttpServletResponse) res).setStatus(400);
        };

    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/roles/admin");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);

    verify(executionService).recordCompletion(execId, "FAILED", "Direct error message");
  }

  @Test
  void authenticatedFailedRequestWithNonJsonBody() throws ServletException, IOException {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(
            eq("user@example.com"), eq("role.delete"), eq("DELETE"), any()))
        .thenReturn(execution);

    FilterChain chain =
        (req, res) -> {
          String plain = "A".repeat(600);
          res.getOutputStream().write(plain.getBytes(StandardCharsets.UTF_8));
          ((HttpServletResponse) res).setStatus(500);
        };

    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/roles/admin");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);

    verify(executionService).recordCompletion(execId, "FAILED", "A".repeat(500));
  }

  @Test
  void authenticatedFailedRequestWithEmptyBody() throws ServletException, IOException {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(
            eq("user@example.com"), eq("role.delete"), eq("DELETE"), any()))
        .thenReturn(execution);

    FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(502);

    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/roles/admin");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);

    verify(executionService).recordCompletion(execId, "FAILED", "HTTP 502 error");
  }

  @Test
  void authenticatedFailedRequestHandlesMalformedAndUnrecognizedJsonErrors()
      throws ServletException, IOException {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    String[] bodies = {"{broken}", "{\"error\":\"bad\"}", "{\"error\":{}}"};
    String[] expected = {"HTTP 400 error", bodies[1], bodies[2]};
    for (int i = 0; i < bodies.length; i++) {
      ObjectId execId = new ObjectId();
      Execution execution = new Execution();
      execution.setId(execId);
      when(executionService.recordReceived(eq("user@example.com"), eq("user.me"), eq("GET"), any()))
          .thenReturn(execution);
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
      MockHttpServletResponse response = new MockHttpServletResponse();
      String body = bodies[i];
      filter.doFilter(
          request,
          response,
          (req, res) -> {
            res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            ((HttpServletResponse) res).setStatus(400);
          });
      verify(executionService).recordCompletion(execId, "FAILED", expected[i]);
    }
  }

  @Test
  void executionErrorDetailOverridesResponseBody() throws ServletException, IOException {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");
    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(eq("user@example.com"), any(), eq("GET"), any()))
        .thenReturn(execution);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          req.setAttribute("EXECUTION_ERROR_DETAIL", "sanitized internal detail");
          ((HttpServletResponse) res).setStatus(400);
        });

    verify(executionService).recordCompletion(execId, "FAILED", "sanitized internal detail");
  }

  @Test
  void authenticatedRequestExceptionRecordsFailureAndRethrows() {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "user@example.com", "pass", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    ObjectId execId = new ObjectId();
    Execution execution = new Execution();
    execution.setId(execId);
    when(executionService.recordReceived(eq("user@example.com"), eq("user.me"), eq("GET"), any()))
        .thenReturn(execution);

    FilterChain chain =
        (req, res) -> {
          throw new RuntimeException("Server crash");
        };

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Server crash");

    verify(executionService).recordCompletion(execId, "FAILED", "Server crash");
  }

  @Test
  void testResolveEventNameMappings() {
    assertThat(filter.resolveEventName("GET", "/api/v1/members")).isEqualTo("member.list");
    assertThat(filter.resolveEventName("POST", "/api/v1/members")).isEqualTo("member.create");
    assertThat(filter.resolveEventName("GET", "/api/v1/members/by-member-id/123"))
        .isEqualTo("member.get-by-member-id");
    assertThat(filter.resolveEventName("GET", "/api/v1/members/by-email/a@b.com"))
        .isEqualTo("member.get-by-email");
    assertThat(filter.resolveEventName("POST", "/api/v1/members/123/restore"))
        .isEqualTo("member.restore");
    assertThat(filter.resolveEventName("PATCH", "/api/v1/members/123")).isEqualTo("member.update");
    assertThat(filter.resolveEventName("DELETE", "/api/v1/members/123")).isEqualTo("member.delete");
    assertThat(filter.resolveEventName("GET", "/api/v1/members/123")).isEqualTo("member.get-by-id");
    assertThat(filter.resolveEventName("HEAD", "/api/v1/members/123")).isEqualTo("member.head");

    assertThat(filter.resolveEventName("GET", "/api/v1/roles")).isEqualTo("role.list");
    assertThat(filter.resolveEventName("POST", "/api/v1/roles")).isEqualTo("role.create");
    assertThat(filter.resolveEventName("PUT", "/api/v1/roles/admin")).isEqualTo("role.update");
    assertThat(filter.resolveEventName("DELETE", "/api/v1/roles/admin")).isEqualTo("role.delete");
    assertThat(filter.resolveEventName("GET", "/api/v1/roles/admin")).isEqualTo("role.get");
    assertThat(filter.resolveEventName("HEAD", "/api/v1/roles/admin")).isEqualTo("role.head");

    assertThat(filter.resolveEventName("GET", "/api/v1/users")).isEqualTo("user.list");
    assertThat(filter.resolveEventName("POST", "/api/v1/users/identity"))
        .isEqualTo("user.identity.upsert");
    assertThat(filter.resolveEventName("PUT", "/api/v1/users/identity/123/roles"))
        .isEqualTo("user.roles.update");
    assertThat(filter.resolveEventName("PUT", "/api/v1/users/123/roles"))
        .isEqualTo("user.roles.update");
    assertThat(filter.resolveEventName("PUT", "/api/v1/users/123")).isEqualTo("user.status.update");
    assertThat(filter.resolveEventName("HEAD", "/api/v1/users/123")).isEqualTo("user.head");

    assertThat(filter.resolveEventName("GET", "/api/v1/me")).isEqualTo("user.me");
    assertThat(filter.resolveEventName("GET", "/api/v1/other")).isEqualTo("api.get");
  }
}
