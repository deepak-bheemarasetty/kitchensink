package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.RolePermissionService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Tests Auth0 client registration configuration.
 *
 * @author Deepak Bheemarasetty
 */
class SecurityConfigurationTest {
  @Test
  void clientRegistrationAddsTrailingSlashToIssuerWhenMissing() {
    ClientRegistrationRepository registrations =
        new SecurityConfiguration()
            .clientRegistrationRepository("https://tenant.example", "client-id", "client-secret");

    assertThat(
            registrations.findByRegistrationId("auth0").getProviderDetails().getAuthorizationUri())
        .isEqualTo("https://tenant.example/authorize");
  }

  @Test
  void clientRegistrationKeepsExistingIssuerSlash() {
    ClientRegistrationRepository registrations =
        new SecurityConfiguration()
            .clientRegistrationRepository("https://tenant.example/", "client-id", "client-secret");

    assertThat(
            registrations.findByRegistrationId("auth0").getProviderDetails().getAuthorizationUri())
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

  @Test
  void jwtConverterRequiresVerifiedEmailAndUsesIdentityFallbacks() {
    SecurityConfiguration configuration = new SecurityConfiguration();
    RolePermissionService permissions = mock(RolePermissionService.class);
    JwtAuthenticationConverter converter = configuration.jwtAuthenticationConverter(permissions);
    when(permissions.user("verified@example.com"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("member:view")));
    when(permissions.user("subject-user"))
        .thenReturn(new RolePermissionService.UserPermissions(false, Set.of()));

    Authentication unverified =
        converter.convert(jwt("token-1", "verified@example.com", null, false, "unverified"));
    assertThat(unverified.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .doesNotContain("member:view");
    verify(permissions, never()).user("verified@example.com");

    Authentication emailVerified =
        converter.convert(jwt("token-2", "verified@example.com", null, "true", "subject-user"));
    assertThat(emailVerified.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("member:view");

    Authentication urlFallback =
        converter.convert(jwt("token-3", null, "verified@example.com", true, "subject-user"));
    assertThat(urlFallback.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("member:view");

    Authentication subjectFallback =
        converter.convert(jwt("token-4", null, null, true, "subject-user"));
    assertThat(subjectFallback.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .doesNotContain("member:view");
    verify(permissions).user("subject-user");
  }

  @Test
  void oauthMapperMapsVerifiedOidcAndOauthAuthoritiesOnlyForActiveUsers() {
    SecurityConfiguration configuration = new SecurityConfiguration();
    RolePermissionService permissions = mock(RolePermissionService.class);
    GrantedAuthoritiesMapper mapper = configuration.oauthPermissionMapper(permissions);
    when(permissions.user("active@example.com"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("member:view")));
    when(permissions.user("inactive@example.com"))
        .thenReturn(new RolePermissionService.UserPermissions(false, Set.of()));

    OidcIdToken verifiedIdToken =
        new OidcIdToken(
            "id-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            java.util.Map.of(
                "iss", "https://issuer.example",
                "sub", "active",
                "email", "active@example.com",
                "email_verified", true));
    Collection<? extends GrantedAuthority> oidcAuthorities =
        mapper.mapAuthorities(List.of(new OidcUserAuthority(verifiedIdToken)));
    assertThat(oidcAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .contains("OIDC_USER", "member:view");

    OidcUserInfo userInfo =
        new OidcUserInfo(java.util.Map.of("email", "active@example.com", "email_verified", "true"));
    Collection<? extends GrantedAuthority> userInfoAuthorities =
        mapper.mapAuthorities(List.of(new OidcUserAuthority(verifiedIdToken, userInfo)));
    assertThat(userInfoAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .contains("OIDC_USER", "member:view");

    Collection<? extends GrantedAuthority> oauthAuthorities =
        mapper.mapAuthorities(
            List.of(
                new OAuth2UserAuthority(
                    java.util.Map.of("email", "active@example.com", "email_verified", "true"))));
    assertThat(oauthAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .contains("OAUTH2_USER", "member:view");

    Collection<? extends GrantedAuthority> inactiveAuthorities =
        mapper.mapAuthorities(
            List.of(
                new OAuth2UserAuthority(
                    java.util.Map.of("email", "inactive@example.com", "email_verified", true))));
    assertThat(inactiveAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("OAUTH2_USER");

    Collection<? extends GrantedAuthority> unverifiedAuthorities =
        mapper.mapAuthorities(
            List.of(
                new OAuth2UserAuthority(
                    java.util.Map.of("email", "active@example.com", "email_verified", false))));
    assertThat(unverifiedAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("OAUTH2_USER");
    Collection<? extends GrantedAuthority> nonStringEmailAuthorities =
        mapper.mapAuthorities(
            List.of(
                new OAuth2UserAuthority(java.util.Map.of("email", 123, "email_verified", true))));
    assertThat(nonStringEmailAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("OAUTH2_USER");
    Collection<? extends GrantedAuthority> unrelatedAuthorities =
        mapper.mapAuthorities(List.of(new SimpleGrantedAuthority("ROLE_USER")));
    assertThat(unrelatedAuthorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
  }

  @Test
  void loginSuccessHandlerUpdatesLocalLastLoginAndRedirects() throws Exception {
    SecurityConfiguration configuration = new SecurityConfiguration();
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IdentityResolver identityResolver = mock(IdentityResolver.class);
    Authentication authentication = mock(Authentication.class);
    when(identityResolver.resolveIdentity(authentication)).thenReturn("active@example.com");

    AuthenticationSuccessHandler handler =
        configuration.loginSuccessHandler(mongoTemplate, identityResolver);
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

    verify(mongoTemplate)
        .updateFirst(
            any(Query.class),
            any(org.springframework.data.mongodb.core.query.Update.class),
            eq("users"));
    assertThat(response.getRedirectedUrl()).isEqualTo("/");
  }

  private static Jwt jwt(
      String token, String email, String fallbackEmail, Object verified, String subject) {
    Jwt.Builder builder =
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject(subject)
            .claim("email_verified", verified);
    if (email != null) {
      builder.claim("email", email);
    }
    if (fallbackEmail != null) {
      builder.claim("https://kitchensink/email", fallbackEmail);
    }
    return builder.build();
  }
}
