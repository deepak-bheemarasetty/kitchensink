package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

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
}
