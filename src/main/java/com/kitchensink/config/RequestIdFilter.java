package com.kitchensink.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds a safe request ID to every response and the structured logging context.
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Request-ID");
    String requestId =
        supplied != null && SAFE_ID.matcher(supplied).matches()
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", requestId);
    MDC.put("requestId", requestId);
    String clientIp = resolveClientIp(request);
    MDC.put("clientIp", clientIp);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
      MDC.remove("clientIp");
    }
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedHeader = request.getHeader("X-Forwarded-For");
    if (forwardedHeader != null && !forwardedHeader.isBlank()) {
      String[] parts = forwardedHeader.split(",");
      String rightmostIp = parts[parts.length - 1].trim();
      if (!rightmostIp.isEmpty() && rightmostIp.length() <= 64) {
        return rightmostIp;
      }
    }
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null && remoteAddr.length() <= 64 ? remoteAddr : "unknown";
  }
}
