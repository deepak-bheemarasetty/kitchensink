package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for CustomApiMetricsFilter and CustomDbMetricsConfiguration.
 *
 * @author Deepak Bheemarasetty
 */
class CustomMetricsTest {

  private MeterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
  }

  @Test
  void apiMetricsFilterRecordsMetricsWithNormalizedUris() throws ServletException, IOException {
    CustomApiMetricsFilter filter = new CustomApiMetricsFilter(registry, "kitchensink");

    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/kitchensink/api/v1/members/6af04357f74949c479579292");
    request.setContextPath("/kitchensink");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    FilterChain chain = (req, res) -> {};
    filter.doFilter(request, response, chain);

    Timer timer =
        registry
            .find("app.api.requests")
            .tag("api", "GET /api/v1/members/{id}")
            .tag("status", "200")
            .tag("application-name", "kitchensink")
            .timer();

    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }

  @Test
  void dbMetricsCommandListenerRecordsCommandSuccessAndFailure() {
    final CustomDbMetricsConfiguration.CustomDbMetricsCommandListener listener =
        new CustomDbMetricsConfiguration.CustomDbMetricsCommandListener(registry, "kitchensink");

    ConnectionDescription connection = mock(ConnectionDescription.class);
    when(connection.getServerId())
        .thenReturn(new ServerId(new com.mongodb.connection.ClusterId(), new ServerAddress()));

    BsonDocument findDoc = new BsonDocument("find", new BsonString("members"));
    CommandStartedEvent startFind =
        new CommandStartedEvent(1, 100, connection, "kitchensink", "find", findDoc);
    listener.commandStarted(startFind);

    CommandSucceededEvent successFind =
        new CommandSucceededEvent(
            1, 100, connection, "kitchensink", "find", new BsonDocument(), 5000000);
    listener.commandSucceeded(successFind);

    Timer successTimer =
        registry
            .find("app.db.operations")
            .tag("model", "members")
            .tag("method", "find")
            .tag("status", "SUCCESS")
            .tag("application-name", "kitchensink")
            .timer();

    assertThat(successTimer).isNotNull();
    assertThat(successTimer.count()).isEqualTo(1);

    // Test failure
    BsonDocument insertDoc = new BsonDocument("insert", new BsonString("users"));
    CommandStartedEvent startInsert =
        new CommandStartedEvent(2, 101, connection, "kitchensink", "insert", insertDoc);
    listener.commandStarted(startInsert);

    CommandFailedEvent failInsert =
        new CommandFailedEvent(
            2, 101, connection, "kitchensink", "insert", 2000000, new RuntimeException("DB error"));
    listener.commandFailed(failInsert);

    Timer failTimer =
        registry
            .find("app.db.operations")
            .tag("model", "users")
            .tag("method", "insert")
            .tag("status", "FAILED")
            .tag("application-name", "kitchensink")
            .timer();

    assertThat(failTimer).isNotNull();
    assertThat(failTimer.count()).isEqualTo(1);

    // Test internal command is ignored
    BsonDocument pingDoc = new BsonDocument("ping", new BsonString("1"));
    CommandStartedEvent pingStart =
        new CommandStartedEvent(3, 102, connection, "kitchensink", "ping", pingDoc);
    listener.commandStarted(pingStart);
    assertThat(registry.find("app.db.operations").tag("method", "ping").timer()).isNull();

    // Verify customizer bean creation
    final CustomDbMetricsConfiguration config = new CustomDbMetricsConfiguration();
    assertThat(config.customDbMetricsCustomizer(registry, "kitchensink")).isNotNull();
  }
}
