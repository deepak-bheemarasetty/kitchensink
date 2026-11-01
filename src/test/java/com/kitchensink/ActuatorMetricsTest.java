package com.kitchensink;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies authenticated HTTP and MongoDB Actuator metrics. */
@SpringBootTest(
    properties = {
      "spring.security.user.name=metrics-test",
      "spring.security.user.password=metrics-password"
    })
@AutoConfigureMockMvc
class ActuatorMetricsTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void metricsEndpointRequiresAuthenticationAndListsHttpMetrics() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/actuator/metrics").with(httpBasic("metrics-test", "metrics-password")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("http.server.requests")));
  }

  @Test
  void mongodbCommandMetricsAreExposed() throws Exception {
    mockMvc
        .perform(
            get("/actuator/metrics/mongodb.driver.commands")
                .with(httpBasic("metrics-test", "metrics-password")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.commands")));
  }

  @Test
  void mongodbConnectionPoolUtilizationMetricsAreExposed() throws Exception {
    mockMvc
        .perform(
            get("/actuator/metrics/mongodb.driver.pool.checkedout")
                .with(httpBasic("metrics-test", "metrics-password")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.pool.checkedout")));
    mockMvc
        .perform(
            get("/actuator/metrics/mongodb.driver.pool.size")
                .with(httpBasic("metrics-test", "metrics-password")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.pool.size")));
    mockMvc
        .perform(
            get("/actuator/metrics/mongodb.driver.pool.waitqueuesize")
                .with(httpBasic("metrics-test", "metrics-password")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.pool.waitqueuesize")));
  }
}
