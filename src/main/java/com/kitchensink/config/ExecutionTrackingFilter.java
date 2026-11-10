package com.kitchensink.config;

import com.kitchensink.model.Execution;
import com.kitchensink.service.ExecutionService;
import com.kitchensink.service.IdentityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.bson.Document;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Filter that tracks authenticated API requests in the executions MongoDB collection.
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ExecutionTrackingFilter extends OncePerRequestFilter {

  private final ExecutionService executionService;
  private final IdentityResolver identityResolver;

  /**
   * Constructs a new execution tracking filter.
   *
   * @param executionService service for managing Execution records
   * @param identityResolver resolver for extracting user email/identity
   */
  public ExecutionTrackingFilter(
      ExecutionService executionService, IdentityResolver identityResolver) {
    this.executionService = executionService;
    this.identityResolver = identityResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    if (path.isEmpty()) {
      path = "/";
    }

    // Only track authenticated API endpoints
    if (!path.startsWith("/api/v1/")) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      filterChain.doFilter(request, response);
      return;
    }

    String userEmail;
    try {
      userEmail = identityResolver.resolveIdentity(auth);
    } catch (Exception ex) {
      filterChain.doFilter(request, response);
      return;
    }

    String method = request.getMethod().toUpperCase();
    String eventName = resolveEventName(method, path);
    String requestId = MDC.get("requestId");
    if (requestId == null || requestId.isBlank()) {
      requestId = request.getHeader("X-Request-ID");
    }

    Execution execution = executionService.recordReceived(userEmail, eventName, method, requestId);

    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
    Exception caughtException = null;

    try {
      filterChain.doFilter(request, wrappedResponse);
    } catch (Exception ex) {
      caughtException = ex;
      throw ex;
    } finally {
      int statusCode = wrappedResponse.getStatus();
      if (execution != null) {
        if (caughtException != null) {
          executionService.recordCompletion(
              execution.getId(), "FAILED", caughtException.getMessage());
        } else if (statusCode >= 400) {
          String errorMessage = extractErrorMessage(wrappedResponse, statusCode);
          executionService.recordCompletion(execution.getId(), "FAILED", errorMessage);
        } else {
          executionService.recordCompletion(execution.getId(), "SUCCESS", null);
        }
      }
      wrappedResponse.copyBodyToResponse();
    }
  }

  String resolveEventName(String method, String path) {
    // Member APIs
    if (path.startsWith("/api/v1/members")) {
      if (path.equals("/api/v1/members")) {
        return "POST".equals(method) ? "member.create" : "member.list";
      }
      if (path.startsWith("/api/v1/members/by-member-id/")) {
        return "member.get-by-member-id";
      }
      if (path.startsWith("/api/v1/members/by-email/")) {
        return "member.get-by-email";
      }
      if (path.endsWith("/restore")) {
        return "member.restore";
      }
      if ("PATCH".equals(method)) {
        return "member.update";
      }
      if ("DELETE".equals(method)) {
        return "member.delete";
      }
      if ("GET".equals(method)) {
        return "member.get-by-id";
      }
      return "member." + method.toLowerCase();
    }

    // Role APIs
    if (path.startsWith("/api/v1/roles")) {
      if (path.equals("/api/v1/roles")) {
        return "POST".equals(method) ? "role.create" : "role.list";
      }
      if ("PUT".equals(method)) {
        return "role.update";
      }
      if ("DELETE".equals(method)) {
        return "role.delete";
      }
      if ("GET".equals(method)) {
        return "role.get";
      }
      return "role." + method.toLowerCase();
    }

    // User APIs
    if (path.startsWith("/api/v1/users")) {
      if (path.equals("/api/v1/users")) {
        return "user.list";
      }
      if (path.startsWith("/api/v1/users/identity")) {
        if ("POST".equals(method)) {
          return "user.identity.upsert";
        }
        if (path.endsWith("/roles")) {
          return "user.roles.update";
        }
      }
      if (path.endsWith("/roles")) {
        return "user.roles.update";
      }
      if ("PUT".equals(method)) {
        return "user.status.update";
      }
      return "user." + method.toLowerCase();
    }

    // Current user API
    if (path.equals("/api/v1/me")) {
      return "user.me";
    }

    return "api." + method.toLowerCase();
  }

  private String extractErrorMessage(
      ContentCachingResponseWrapper wrappedResponse, int statusCode) {
    try {
      byte[] content = wrappedResponse.getContentAsByteArray();
      if (content.length > 0) {
        String body = new String(content, StandardCharsets.UTF_8).trim();
        if (body.startsWith("{") && body.endsWith("}")) {
          Document doc = Document.parse(body);
          if (doc.containsKey("error")) {
            Object errorObj = doc.get("error");
            if (errorObj instanceof Document errorDoc && errorDoc.containsKey("message")) {
              return errorDoc.getString("message");
            }
          }
          if (doc.containsKey("message")) {
            return doc.getString("message");
          }
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
      }
    } catch (Exception ignored) {
      // Fallback
    }
    return "HTTP " + statusCode + " error";
  }
}
