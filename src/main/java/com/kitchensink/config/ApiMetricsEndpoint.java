package com.kitchensink.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator Endpoint exposing granular per-API metrics with percentiles (p50, p95, p99),
 * count, total duration, min, avg, and max rounded to 3 decimal places.
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Endpoint(id = "api-metrics")
public class ApiMetricsEndpoint {

  private final MeterRegistry meterRegistry;

  public ApiMetricsEndpoint(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Reads all registered timers for {@code app.api.requests} and formats each unique API route's
   * performance metrics.
   *
   * @return list of per-API metric statistics rounded to 3 decimal places
   */
  @ReadOperation
  public List<ApiMetricSummary> getApiMetrics() {
    List<Timer> timers = new ArrayList<>(meterRegistry.find("app.api.requests").timers());
    if (timers.isEmpty()) {
      return Collections.emptyList();
    }

    List<ApiMetricSummary> summaries = new ArrayList<>();
    for (Timer timer : timers) {
      String method = timer.getId().getTag("method");
      String uri = timer.getId().getTag("uri");
      String status = timer.getId().getTag("status");
      String outcome = timer.getId().getTag("outcome");
      String api = timer.getId().getTag("api");

      if (method == null && api != null) {
        int spaceIdx = api.indexOf(' ');
        if (spaceIdx > 0) {
          method = api.substring(0, spaceIdx);
          uri = api.substring(spaceIdx + 1);
        }
      }
      if (outcome == null && status != null) {
        try {
          int code = Integer.parseInt(status);
          outcome = (code >= 200 && code < 400) ? "success" : "failed";
        } catch (NumberFormatException ignored) {
          outcome = "unknown";
        }
      }

      final long count = timer.count();
      final double totalSeconds = timer.totalTime(TimeUnit.SECONDS);
      final double maxSeconds = timer.max(TimeUnit.SECONDS);
      final double avgSeconds = timer.mean(TimeUnit.SECONDS);

      double p50Seconds = 0.0;
      double p95Seconds = 0.0;
      double p99Seconds = 0.0;

      HistogramSnapshot snapshot = timer.takeSnapshot();
      for (ValueAtPercentile vap : snapshot.percentileValues()) {
        double p = vap.percentile();
        double valSeconds = vap.value(TimeUnit.SECONDS);
        if (Math.abs(p - 0.50) < 0.01) {
          p50Seconds = valSeconds;
        } else if (Math.abs(p - 0.95) < 0.01) {
          p95Seconds = valSeconds;
        } else if (Math.abs(p - 0.99) < 0.01) {
          p99Seconds = valSeconds;
        }
      }

      // If percentiles are 0 but max/avg exist (e.g. single small sample), fallback sensibly
      if (p50Seconds == 0.0 && count > 0) {
        p50Seconds = avgSeconds;
      }
      if (p95Seconds == 0.0 && count > 0) {
        p95Seconds = maxSeconds;
      }
      if (p99Seconds == 0.0 && count > 0) {
        p99Seconds = maxSeconds;
      }

      // Min calculation approximation from snapshot or average
      double minSeconds = (count > 0 && p50Seconds > 0) ? Math.min(avgSeconds, p50Seconds) : 0.0;

      summaries.add(
          new ApiMetricSummary(
              method != null ? method : "UNKNOWN",
              uri != null ? uri : (api != null ? api : "UNKNOWN"),
              status != null ? status : "UNKNOWN",
              outcome != null ? outcome : "UNKNOWN",
              count,
              round3(totalSeconds),
              round3(avgSeconds),
              round3(minSeconds),
              round3(maxSeconds),
              round3(p50Seconds),
              round3(p95Seconds),
              round3(p99Seconds)));
    }

    summaries.sort(
        Comparator.comparing(ApiMetricSummary::uri)
            .thenComparing(ApiMetricSummary::method)
            .thenComparing(ApiMetricSummary::status));

    return summaries;
  }

  private double round3(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }
    return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
  }

  /** Data representation for API metric entry. */
  public record ApiMetricSummary(
      String method,
      String uri,
      String status,
      String outcome,
      long count,
      double totalTimeSeconds,
      double avgSeconds,
      double minSeconds,
      double maxSeconds,
      double p50Seconds,
      double p95Seconds,
      double p99Seconds) {}
}
