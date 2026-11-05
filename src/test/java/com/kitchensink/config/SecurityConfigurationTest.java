package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Tests Auth0 client registration configuration. */
class SecurityConfigurationTest {
  @Test
  void clientRegistrationAddsTrailingSlashToIssuerWhenMissing() {
    ClientRegistrationRepository registrations =
        new SecurityConfiguration()
            .clientRegistrationRepository(
                "https://tenant.example", "client-id", "client-secret");

    assertThat(
            registrations
                .findByRegistrationId("auth0")
                .getProviderDetails()
                .getAuthorizationUri())
        .isEqualTo("https://tenant.example/authorize");
  }

  @Test
  void clientRegistrationKeepsExistingIssuerSlash() {
    ClientRegistrationRepository registrations =
        new SecurityConfiguration()
            .clientRegistrationRepository(
                "https://tenant.example/", "client-id", "client-secret");

    assertThat(
            registrations
                .findByRegistrationId("auth0")
                .getProviderDetails()
                .getAuthorizationUri())
        .isEqualTo("https://tenant.example/authorize");
  }

  @Test
  void authorizationRequestPromptsForLogin() {
    SecurityConfiguration configuration = new SecurityConfiguration();
    ClientRegistrationRepository registrations =
        configuration.clientRegistrationRepository(
            "https://tenant.example", "client-id", "client-secret");

    OAuth2AuthorizationRequest request =
        configuration
            .authorizationRequestResolver(registrations)
            .resolve(new MockHttpServletRequest("GET", "/oauth2/authorization/auth0"));

    assertThat(request).isNotNull();
    assertThat(request.getAdditionalParameters()).containsEntry("prompt", "login");
  }
}
