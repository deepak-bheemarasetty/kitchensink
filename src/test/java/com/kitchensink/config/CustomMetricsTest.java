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
            .tag("method", "GET")
            .tag("uri", "/api/v1/members/{id}")
            .tag("status", "200")
            .tag("outcome", "success")
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

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles");
    request.setContextPath("");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    FilterChain chain = (req, res) -> {};
    filter.doFilter(request, response, chain);

    Timer timer =
        registry
            .find("app.api.requests")
            .tag("api", "GET /api/v1/roles")
            .tag("method", "GET")
            .tag("uri", "/api/v1/roles")
            .tag("status", "200")
            .tag("outcome", "success")
            .tag("application-name", "kitchensink")
            .timer();

    assertThat(timer).isNotNull();

    // Verify non-api/v1 paths (like static resources or UI /) are ignored
    MockHttpServletRequest uiRequest = new MockHttpServletRequest("GET", "/members");
    uiRequest.setContextPath("");
    filter.doFilter(uiRequest, response, chain);
    Timer uiTimer = registry.find("app.api.requests").tag("api", "GET /members").timer();
    assertThat(uiTimer).isNull();
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

  @Test
  void apiMetricsEndpointFormatsMetricsWithPrecisionAndPercentiles()
      throws ServletException, IOException {
    CustomApiMetricsFilter filter = new CustomApiMetricsFilter(registry, "kitchensink");

    // Simulate 4 GET /api/v1/members calls (success)
    for (int i = 0; i < 4; i++) {
      MockHttpServletRequest getReq =
          new MockHttpServletRequest("GET", "/kitchensink/api/v1/members");
      getReq.setContextPath("/kitchensink");
      MockHttpServletResponse getRes = new MockHttpServletResponse();
      getRes.setStatus(200);
      filter.doFilter(getReq, getRes, (req, res) -> {});
    }

    // Simulate 2 POST /api/v1/members calls (success)
    for (int i = 0; i < 2; i++) {
      MockHttpServletRequest postReq =
          new MockHttpServletRequest("POST", "/kitchensink/api/v1/members");
      postReq.setContextPath("/kitchensink");
      MockHttpServletResponse postRes = new MockHttpServletResponse();
      postRes.setStatus(201);
      filter.doFilter(postReq, postRes, (req, res) -> {});
    }

    // Simulate 1 POST /api/v1/users calls (failed)
    MockHttpServletRequest failedReq =
        new MockHttpServletRequest("POST", "/kitchensink/api/v1/users");
    failedReq.setContextPath("/kitchensink");
    MockHttpServletResponse failedRes = new MockHttpServletResponse();
    failedRes.setStatus(400);
    filter.doFilter(failedReq, failedRes, (req, res) -> {});

    ApiMetricsEndpoint endpoint = new ApiMetricsEndpoint(registry);
    var metrics = endpoint.getApiMetrics();

    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSize(3);

    // Verify GET /api/v1/members
    var getMembers =
        metrics.stream()
            .filter(m -> "GET".equals(m.method()) && "/api/v1/members".equals(m.uri()))
            .findFirst()
            .orElse(null);
    assertThat(getMembers).isNotNull();
    assertThat(getMembers.count()).isEqualTo(4);
    assertThat(getMembers.status()).isEqualTo("200");
    assertThat(getMembers.outcome()).isEqualTo("success");
    assertThat(getMembers.totalTimeSeconds()).isGreaterThanOrEqualTo(0.0);
    assertThat(getMembers.maxSeconds()).isGreaterThanOrEqualTo(0.0);
    assertThat(getMembers.p95Seconds()).isGreaterThanOrEqualTo(0.0);

    // Verify POST /api/v1/members
    var postMembers =
        metrics.stream()
            .filter(m -> "POST".equals(m.method()) && "/api/v1/members".equals(m.uri()))
            .findFirst()
            .orElse(null);
    assertThat(postMembers).isNotNull();
    assertThat(postMembers.count()).isEqualTo(2);
    assertThat(postMembers.status()).isEqualTo("201");
    assertThat(postMembers.outcome()).isEqualTo("success");

    // Verify POST /api/v1/users
    var postUsers =
        metrics.stream()
            .filter(m -> "POST".equals(m.method()) && "/api/v1/users".equals(m.uri()))
            .findFirst()
            .orElse(null);
    assertThat(postUsers).isNotNull();
    assertThat(postUsers.count()).isEqualTo(1);
    assertThat(postUsers.status()).isEqualTo("400");
    assertThat(postUsers.outcome()).isEqualTo("failed");
  }

  @Test
  void dbMetricsEndpointFormatsMetricsWithPrecisionAndPercentiles() {
    final CustomDbMetricsConfiguration.CustomDbMetricsCommandListener listener =
        new CustomDbMetricsConfiguration.CustomDbMetricsCommandListener(registry, "kitchensink");

    // Simulate 3 find operations on members (SUCCESS)
    for (int i = 0; i < 3; i++) {
      BsonDocument findDoc = new BsonDocument("find", new BsonString("members"));
      CommandStartedEvent start = mock(CommandStartedEvent.class);
      when(start.getRequestId()).thenReturn(300 + i);
      when(start.getCommandName()).thenReturn("find");
      when(start.getCommand()).thenReturn(findDoc);
      listener.commandStarted(start);

      CommandSucceededEvent success = mock(CommandSucceededEvent.class);
      when(success.getRequestId()).thenReturn(300 + i);
      when(success.getCommandName()).thenReturn("find");
      when(success.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(10_000_000L);
      listener.commandSucceeded(success);
    }

    // Simulate 1 insert operation on users (FAILED)
    BsonDocument insertDoc = new BsonDocument("insert", new BsonString("users"));
    CommandStartedEvent startFail = mock(CommandStartedEvent.class);
    when(startFail.getRequestId()).thenReturn(400);
    when(startFail.getCommandName()).thenReturn("insert");
    when(startFail.getCommand()).thenReturn(insertDoc);
    listener.commandStarted(startFail);

    CommandFailedEvent fail = mock(CommandFailedEvent.class);
    when(fail.getRequestId()).thenReturn(400);
    when(fail.getCommandName()).thenReturn("insert");
    when(fail.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(5_000_000L);
    listener.commandFailed(fail);

    DbMetricsEndpoint endpoint = new DbMetricsEndpoint(registry);
    var dbSummaries = endpoint.getDbMetrics();

    assertThat(dbSummaries).isNotEmpty();
    assertThat(dbSummaries).hasSize(2);

    var membersFind =
        dbSummaries.stream()
            .filter(m -> "members".equals(m.model()) && "find".equals(m.method()))
            .findFirst()
            .orElse(null);
    assertThat(membersFind).isNotNull();
    assertThat(membersFind.count()).isEqualTo(3);
    assertThat(membersFind.status()).isEqualTo("SUCCESS");
    assertThat(membersFind.totalTimeSeconds()).isGreaterThanOrEqualTo(0.0);
    assertThat(membersFind.maxSeconds()).isGreaterThanOrEqualTo(0.0);
    assertThat(membersFind.p95Seconds()).isGreaterThanOrEqualTo(0.0);

    var usersInsert =
        dbSummaries.stream()
            .filter(m -> "users".equals(m.model()) && "insert".equals(m.method()))
            .findFirst()
            .orElse(null);
    assertThat(usersInsert).isNotNull();
    assertThat(usersInsert.count()).isEqualTo(1);
    assertThat(usersInsert.status()).isEqualTo("FAILED");
  }
}
