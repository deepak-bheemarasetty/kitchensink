package com.kitchensink.config;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bson.BsonValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom metrics listener that records database operations (model, repo method/command, count,
 * time, application-name) into Micrometer.
 *
 * @author Deepak Bheemarasetty
 */
@Configuration
public class CustomDbMetricsConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer customDbMetricsCustomizer(
      MeterRegistry meterRegistry,
      @Value("${spring.application.name:${management.metrics.tags.application:kitchensink}}")
          String applicationName) {
    return builder ->
        builder.addCommandListener(
            new CustomDbMetricsCommandListener(meterRegistry, applicationName));
  }

  static class CustomDbMetricsCommandListener implements CommandListener {
    private final MeterRegistry meterRegistry;
    private final String applicationName;
    private final Map<Integer, CommandContext> inFlight = new ConcurrentHashMap<>();

    record CommandContext(String commandName, String collection, long startTimeNanos) {}

    CustomDbMetricsCommandListener(MeterRegistry meterRegistry, String applicationName) {
      this.meterRegistry = meterRegistry;
      this.applicationName = applicationName;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
      String commandName = event.getCommandName();
      // Ignore internal heartbeats, handshakes, ping, and buildInfo commands
      if (isInternalCommand(commandName)) {
        return;
      }
      String collection = resolveCollection(event);
      inFlight.put(
          event.getRequestId(), new CommandContext(commandName, collection, System.nanoTime()));
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
      CommandContext ctx = inFlight.remove(event.getRequestId());
      if (ctx != null) {
        long durationNanos = System.nanoTime() - ctx.startTimeNanos();
        record(ctx.collection(), ctx.commandName(), "SUCCESS", durationNanos);
      }
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
      CommandContext ctx = inFlight.remove(event.getRequestId());
      if (ctx != null) {
        long durationNanos = System.nanoTime() - ctx.startTimeNanos();
        record(ctx.collection(), ctx.commandName(), "FAILED", durationNanos);
      }
    }

    private void record(String model, String repoMethod, String status, long durationNanos) {
      Timer.builder("app.db.operations")
          .description(
              "MongoDB operations metrics tracking model, command/method, count and duration")
          .tag("model", model)
          .tag("method", repoMethod)
          .tag("status", status)
          .tag("application-name", applicationName)
          .publishPercentiles(0.5, 0.95, 0.99)
          .publishPercentileHistogram()
          .register(meterRegistry)
          .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String resolveCollection(CommandStartedEvent event) {
      BsonValue firstValue = event.getCommand().get(event.getCommandName());
      if (firstValue != null && firstValue.isString()) {
        return firstValue.asString().getValue();
      }
      return "system";
    }

    private boolean isInternalCommand(String commandName) {
      return "hello".equalsIgnoreCase(commandName)
          || "isMaster".equalsIgnoreCase(commandName)
          || "ping".equalsIgnoreCase(commandName)
          || "buildInfo".equalsIgnoreCase(commandName)
          || "saslStart".equalsIgnoreCase(commandName)
          || "saslContinue".equalsIgnoreCase(commandName);
    }
  }
}
