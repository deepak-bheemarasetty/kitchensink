package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.controller.LoginController;
import com.kitchensink.service.ExecutionService;
import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = LoginController.class,
    properties = {
      "auth0.issuer-uri=https://tenant.example/",
      "auth0.client-id=test-client",
      "auth0.client-secret=test-secret"
    })
@Import({
  SecurityConfiguration.class,
  CurrentPermissionAuthorizationManager.class,
  SecurityFilterChainTest.MetricsConfiguration.class
})
class SecurityFilterChainTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private RolePermissionService rolePermissionService;

  @MockitoBean private IdentityResolver identityResolver;

  @MockitoBean private MongoTemplate mongoTemplate;

  @MockitoBean private ExecutionService executionService;

  @TestConfiguration
  static class MetricsConfiguration {
    @Bean
    MeterRegistry testMeterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @BeforeEach
  void setUp() {
    when(rolePermissionService.user(any()))
        .thenReturn(new RolePermissionService.UserPermissions(false, Set.of()));
  }

  @Test
  void browserChainAllowsLoginAndProtectsMemberPages() throws Exception {
    mockMvc.perform(get("/login")).andExpect(status().isOk());
    mockMvc.perform(get("/members")).andExpect(status().is3xxRedirection());
  }

  @Test
  void apiChainProtectsApiAndActuatorRoutes() throws Exception {
    mockMvc.perform(get("/api/v1/unknown")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/unknown").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1")).andExpect(status().is3xxRedirection());
  }

  @Test
  void apiChainAppliesEachDeclaredMethodAndAuthorityRule() throws Exception {
    var authorities =
        Set.of(
            new SimpleGrantedAuthority("member:view"),
            new SimpleGrantedAuthority("member:create"),
            new SimpleGrantedAuthority("member:update"),
            new SimpleGrantedAuthority("member:delete"),
            new SimpleGrantedAuthority("user:manage"),
            new SimpleGrantedAuthority("role:manage"));
    Object[][] requests = {
      {HttpMethod.GET, "/api/v1/members"},
      {HttpMethod.GET, "/api/v1/members/1"},
      {HttpMethod.GET, "/api/v1/me"},
      {HttpMethod.POST, "/api/v1/members"},
      {HttpMethod.PATCH, "/api/v1/members/1"},
      {HttpMethod.DELETE, "/api/v1/members/1"},
      {HttpMethod.POST, "/api/v1/members/1/restore"},
      {HttpMethod.GET, "/api/v1/users"},
      {HttpMethod.GET, "/api/v1/roles"},
      {HttpMethod.POST, "/api/v1/users/identity"},
      {HttpMethod.PUT, "/api/v1/users/identity/000000000000000000000001"},
      {HttpMethod.PUT, "/api/v1/users/000000000000000000000001"},
      {HttpMethod.POST, "/api/v1/roles"},
      {HttpMethod.PUT, "/api/v1/roles/viewer"},
      {HttpMethod.DELETE, "/api/v1/roles/viewer"},
      {HttpMethod.GET, "/actuator/metrics"},
      {HttpMethod.GET, "/actuator/api-metrics"}
    };

    for (Object[] route : requests) {
      var result =
          mockMvc.perform(
              request((HttpMethod) route[0], (String) route[1])
                  .with(user("admin").authorities(authorities))
                  .with(csrf()));
      result.andExpect(
          response ->
              assertThat(response.getResponse().getStatus())
                  .isNotEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED.value()));
    }
  }
}
