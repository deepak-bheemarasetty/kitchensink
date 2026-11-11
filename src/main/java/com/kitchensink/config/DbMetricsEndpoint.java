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
 * Custom Actuator Endpoint exposing granular per-DB operation metrics with percentiles (p50, p95,
 * p99), count, total duration, min, avg, and max rounded to 3 decimal places.
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Endpoint(id = "db-metrics")
public class DbMetricsEndpoint {

  private final MeterRegistry meterRegistry;

  public DbMetricsEndpoint(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Reads all registered timers for {@code app.db.operations} and formats each unique DB
   * model/method performance metrics.
   *
   * @return list of per-DB operation metric statistics rounded to 3 decimal places
   */
  @ReadOperation
  public List<DbMetricSummary> getDbMetrics() {
    List<Timer> timers = new ArrayList<>(meterRegistry.find("app.db.operations").timers());
    if (timers.isEmpty()) {
      return Collections.emptyList();
    }

    List<DbMetricSummary> summaries = new ArrayList<>();
    for (Timer timer : timers) {
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
      final String model = timer.getId().getTag("model");
      final String method = timer.getId().getTag("method");
      final String status = timer.getId().getTag("status");

      summaries.add(
          new DbMetricSummary(
              model != null ? model : "UNKNOWN",
              method != null ? method : "UNKNOWN",
              status != null ? status : "UNKNOWN",
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
        Comparator.comparing(DbMetricSummary::model)
            .thenComparing(DbMetricSummary::method)
            .thenComparing(DbMetricSummary::status));

    return summaries;
  }

  private double round3(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }
    return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
  }

  /** Data representation for DB metric entry. */
  public record DbMetricSummary(
      String model,
      String method,
      String status,
      long count,
      double totalTimeSeconds,
      double avgSeconds,
      double minSeconds,
      double maxSeconds,
      double p50Seconds,
      double p95Seconds,
      double p99Seconds) {}
}
