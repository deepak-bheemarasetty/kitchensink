package com.kitchensink.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Intercepts incoming HTTP requests to track custom API metrics (api, request status, count, total
 * processing time, application-name).
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CustomApiMetricsFilter extends OncePerRequestFilter {

  private final MeterRegistry meterRegistry;
  private final String applicationName;

  public CustomApiMetricsFilter(
      MeterRegistry meterRegistry,
      @Value("${spring.application.name:${management.metrics.tags.application:kitchensink}}")
          String applicationName) {
    this.meterRegistry = meterRegistry;
    this.applicationName = applicationName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String apiPath =
        (contextPath != null && uri.startsWith(contextPath))
            ? uri.substring(contextPath.length())
            : uri;
    if (apiPath.isEmpty()) {
      apiPath = "/";
    }

    // Only track controller API requests (/api/v1/**), ignore static resources & UI pages
    if (!apiPath.startsWith("/api/v1/")) {
      filterChain.doFilter(request, response);
      return;
    }

    long startTime = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationNanos = System.nanoTime() - startTime;
      recordMetric(request.getMethod(), apiPath, response.getStatus(), durationNanos);
    }
  }

  private void recordMetric(String method, String apiPath, int status, long durationNanos) {
    // Normalize IDs and emails to prevent cardinality explosion
    String normalizedPath =
        apiPath
            .replaceAll("/[0-9a-fA-F]{24}(?=/|$)", "/{id}")
            .replaceAll("/by-email/[^/]+", "/by-email/{email}")
            .replaceAll("/by-member-id/[^/]+", "/by-member-id/{memberId}");

    String httpMethod = method.toUpperCase();
    String normalizedApi = httpMethod + " " + normalizedPath;
    String statusStr = String.valueOf(status);
    String statusGroup = (status >= 200 && status < 400) ? "success" : "failed";

    Timer.builder("app.api.requests")
        .description("API request metrics tracking counts, duration and status")
        .tag("api", normalizedApi)
        .tag("method", httpMethod)
        .tag("uri", normalizedPath)
        .tag("status", statusStr)
        .tag("outcome", statusGroup)
        .tag("application-name", applicationName)
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(meterRegistry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }
}
