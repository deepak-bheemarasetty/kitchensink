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
    long startTime = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationNanos = System.nanoTime() - startTime;
      recordMetric(request, response.getStatus(), durationNanos);
    }
  }

  private void recordMetric(HttpServletRequest request, int status, long durationNanos) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String apiPath =
        (contextPath != null && uri.startsWith(contextPath))
            ? uri.substring(contextPath.length())
            : uri;
    if (apiPath.isEmpty()) {
      apiPath = "/";
    }

    // Normalize IDs to prevent cardinality explosion: replace 24-hex Mongo ObjectIds
    String normalizedApi =
        request.getMethod() + " " + apiPath.replaceAll("/[0-9a-fA-F]{24}(?=/|$)", "/{id}");

    Timer.builder("app.api.requests")
        .description("API request metrics tracking counts, duration and status")
        .tag("api", normalizedApi)
        .tag("status", String.valueOf(status))
        .tag("application-name", applicationName)
        .register(meterRegistry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }
}
