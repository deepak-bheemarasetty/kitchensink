package com.kitchensink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.MemberService;
import com.mongodb.MongoWriteException;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;
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

  @Autowired private MemberRepository memberRepository;

  @Autowired private MemberService memberService;

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
  void mongoSchemaRejectsEmailWithoutDottedDomain() {
    Document invalidMember =
        new Document("_id", Long.MAX_VALUE)
            .append("name", "Jane Doe")
            .append("email", "abc@gmail")
            .append("phoneNumber", "2125551234")
            .append("countryCode", 1)
            .append("createdBy", "test-user")
            .append("creationDate", new Date())
            .append("lastUpdatedBy", "test-user")
            .append("lastUpdateDate", new Date())
            .append("version", 0L);

    assertThatThrownBy(() -> mongoTemplate.getCollection("members").insertOne(invalidMember))
        .isInstanceOf(MongoWriteException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(
                        ((MongoWriteException) exception).getError().getCode())
                    .isEqualTo(121));
  }

  @Test
  void memberSequenceAndAuditMetadataAreStored() {
    Member member = Member.create();
    member.setName("Audit Test");
    member.setEmail("audit-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    member.setPhoneNumber("1234");
    member.setCountryCode(1);

    Member saved = memberService.register(member);
    try {
      assertThat(saved.getId()).isPositive();
      assertThat(saved.getCreatedBy()).isEqualTo("user");
      assertThat(saved.getLastUpdatedBy()).isEqualTo("user");
      assertThat(saved.getCreationDate()).isNotNull();
      assertThat(saved.getLastUpdateDate()).isNotNull();
      assertThat(saved.getVersion()).isZero();
    } finally {
      memberRepository.deleteById(saved.getId());
    }
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
