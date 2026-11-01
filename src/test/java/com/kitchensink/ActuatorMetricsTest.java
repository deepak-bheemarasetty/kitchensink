package com.kitchensink;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies authenticated HTTP and MongoDB Actuator metrics. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ActuatorMetricsTest {
  @Autowired private MockMvc mockMvc;

  @Autowired private MongoTemplate mongoTemplate;

  @Test
  @WithAnonymousUser
  void metricsEndpointRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void metricsEndpointListsHttpMetrics() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("http.server.requests")));
  }

  @Test
  void mongodbCommandMetricsAreExposed() throws Exception {
    mongoTemplate.executeCommand("{ ping: 1 }");

    mockMvc
        .perform(get("/actuator/metrics/mongodb.driver.commands"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.commands")));
  }

  @Test
  void mongodbConnectionPoolUtilizationMetricsAreExposed() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics/mongodb.driver.pool.checkedout"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.pool.checkedout")));
    mockMvc
        .perform(get("/actuator/metrics/mongodb.driver.pool.size"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.pool.size")));
    mockMvc
        .perform(get("/actuator/metrics/mongodb.driver.pool.waitqueuesize"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mongodb.driver.pool.waitqueuesize")));
  }
}
