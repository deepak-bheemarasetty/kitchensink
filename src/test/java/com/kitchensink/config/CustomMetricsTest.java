package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    BsonDocument findDoc = new BsonDocument("find", new BsonString("members"));
    CommandStartedEvent startFind = mock(CommandStartedEvent.class);
    when(startFind.getRequestId()).thenReturn(100);
    when(startFind.getCommandName()).thenReturn("find");
    when(startFind.getCommand()).thenReturn(findDoc);
    listener.commandStarted(startFind);

    CommandSucceededEvent successFind = mock(CommandSucceededEvent.class);
    when(successFind.getRequestId()).thenReturn(100);
    when(successFind.getCommandName()).thenReturn("find");
    when(successFind.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(5_000_000L);
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
    CommandStartedEvent startInsert = mock(CommandStartedEvent.class);
    when(startInsert.getRequestId()).thenReturn(101);
    when(startInsert.getCommandName()).thenReturn("insert");
    when(startInsert.getCommand()).thenReturn(insertDoc);
    listener.commandStarted(startInsert);

    CommandFailedEvent failInsert = mock(CommandFailedEvent.class);
    when(failInsert.getRequestId()).thenReturn(101);
    when(failInsert.getCommandName()).thenReturn("insert");
    when(failInsert.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(2_000_000L);
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
    CommandStartedEvent pingStart = mock(CommandStartedEvent.class);
    when(pingStart.getRequestId()).thenReturn(102);
    when(pingStart.getCommandName()).thenReturn("ping");
    when(pingStart.getCommand()).thenReturn(pingDoc);
    listener.commandStarted(pingStart);
    assertThat(registry.find("app.db.operations").tag("method", "ping").timer()).isNull();

    // Verify customizer bean creation
    final CustomDbMetricsConfiguration config = new CustomDbMetricsConfiguration();
    var customizer = config.customDbMetricsCustomizer(registry, "kitchensink");
    assertThat(customizer).isNotNull();
    var builder = com.mongodb.MongoClientSettings.builder();
    customizer.customize(builder);
  }

  @Test
  void apiMetricsFilterHandlesRootContextPath() throws ServletException, IOException {
    CustomApiMetricsFilter filter = new CustomApiMetricsFilter(registry, "kitchensink");

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    request.setContextPath("");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    FilterChain chain = (req, res) -> {};
    filter.doFilter(request, response, chain);

    Timer timer =
        registry
            .find("app.api.requests")
            .tag("api", "GET /")
            .tag("status", "200")
            .tag("application-name", "kitchensink")
            .timer();

    assertThat(timer).isNotNull();
  }

  @Test
  void dbMetricsCommandListenerHandlesUntrackedEventsAndNonStringCollections() {
    final CustomDbMetricsConfiguration.CustomDbMetricsCommandListener listener =
        new CustomDbMetricsConfiguration.CustomDbMetricsCommandListener(registry, "kitchensink");

    // Succeeded or failed with no started event
    CommandSucceededEvent orphanSuccess = mock(CommandSucceededEvent.class);
    when(orphanSuccess.getRequestId()).thenReturn(999);
    listener.commandSucceeded(orphanSuccess);

    CommandFailedEvent orphanFailed = mock(CommandFailedEvent.class);
    when(orphanFailed.getRequestId()).thenReturn(999);
    listener.commandFailed(orphanFailed);

    // Non-string command value defaults to "system"
    BsonDocument countDoc = new BsonDocument("count", new org.bson.BsonInt32(1));
    CommandStartedEvent startCount = mock(CommandStartedEvent.class);
    when(startCount.getRequestId()).thenReturn(200);
    when(startCount.getCommandName()).thenReturn("count");
    when(startCount.getCommand()).thenReturn(countDoc);
    listener.commandStarted(startCount);

    CommandSucceededEvent successCount = mock(CommandSucceededEvent.class);
    when(successCount.getRequestId()).thenReturn(200);
    when(successCount.getCommandName()).thenReturn("count");
    when(successCount.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(1_000_000L);
    listener.commandSucceeded(successCount);

    assertThat(
            registry
                .find("app.db.operations")
                .tag("model", "system")
                .tag("method", "count")
                .timer())
        .isNotNull();

    // Exercise all internal command filters
    for (String cmd :
        new String[] {"hello", "isMaster", "buildInfo", "saslStart", "saslContinue"}) {
      CommandStartedEvent internalCmd = mock(CommandStartedEvent.class);
      when(internalCmd.getCommandName()).thenReturn(cmd);
      listener.commandStarted(internalCmd);
    }
  }
}
