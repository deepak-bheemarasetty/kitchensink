package com.kitchensink.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

/** Configures OIDC browser login and stateless JWT authentication for the API. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class SecurityConfiguration {
  @Bean
  ClientRegistrationRepository clientRegistrationRepository(
      @Value("${auth0.issuer-uri}") String issuerUri,
      @Value("${auth0.client-id}") String clientId,
      @Value("${auth0.client-secret}") String clientSecret) {
    String issuer = issuerUri.endsWith("/") ? issuerUri : issuerUri + "/";
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("auth0")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri(issuer + "authorize")
            .tokenUri(issuer + "oauth/token")
            .userInfoUri(issuer + "userinfo")
            .userNameAttributeName("name")
            .jwkSetUri(issuer + ".well-known/jwks.json")
            .providerConfigurationMetadata(Map.of("issuer", issuer))
            .clientName("Auth0")
            .build();
    return new InMemoryClientRegistrationRepository(registration);
  }

  @Bean
  @Order(1)
  SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/rest/**", "/actuator/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/login", "/css/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(oauth2Login -> oauth2Login.loginPage("/login"))
        .build();
  }
}
