package com.kitchensink.config;

import java.util.Map;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import com.kitchensink.service.RolePermissionService;

/** Configures OIDC browser login and stateless JWT authentication for the API. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class SecurityConfiguration {
  @Bean
  org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
    return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter(RolePermissionService permissions) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      String username = jwt.getClaimAsString("email");
      if (username == null) username = jwt.getSubject();
      RolePermissionService.UserPermissions user = permissions.user(username);
      Collection<GrantedAuthority> granted = user.active()
          ? user.permissions().stream()
              .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
          : List.of();
      return granted;
    });
    return converter;
  }

  @Bean
  GrantedAuthoritiesMapper oauthPermissionMapper(RolePermissionService permissions) {
    return authorities -> {
      java.util.Set<org.springframework.security.core.GrantedAuthority> mapped =
          new java.util.LinkedHashSet<>();
      for (var authority : authorities) {
        mapped.add(authority);
        String email = null;
        if (authority instanceof OidcUserAuthority oidcAuthority) {
          email = oidcAuthority.getUserInfo() == null
              ? oidcAuthority.getIdToken().getClaimAsString("email")
              : oidcAuthority.getUserInfo().getEmail();
        } else if (authority instanceof OAuth2UserAuthority oauthAuthority) {
          Object value = oauthAuthority.getAttributes().get("email");
          if (value instanceof String emailValue) email = emailValue;
        }
        if (email != null) {
          RolePermissionService.UserPermissions user = permissions.user(email);
          user.permissions().stream().map(SimpleGrantedAuthority::new).forEach(mapped::add);
        }
      }
      return mapped;
    };
  }

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
  OAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    DefaultOAuth2AuthorizationRequestResolver resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(
        builder -> builder.additionalParameters(parameters -> parameters.put("prompt", "login")));
    return resolver;
  }

  @Bean
  @Order(1)
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
      MongoUserDetailsService userDetailsService) throws Exception {
    return http
        .securityMatcher(request -> {
          String path = request.getRequestURI().substring(request.getContextPath().length());
          return path.startsWith("/api/v1/") || path.startsWith("/actuator/");
        })
        .csrf(csrf -> csrf.disable())
        .userDetailsService(userDetailsService)
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, "/api/v1/members", "/api/v1/members/**")
                    .hasAuthority("member:view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/members")
                    .hasAuthority("member:create")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/members/**")
                    .hasAuthority("member:update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/members/**")
                    .hasAuthority("member:delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/members/*/restore")
                    .hasAuthority("member:delete")
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/**")
                    .hasAuthority("user:manage")
                    .requestMatchers(HttpMethod.POST, "/api/v1/users/**")
                    .hasAuthority("user:manage")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/**")
                    .hasAuthority("user:manage")
                    .requestMatchers(HttpMethod.GET, "/api/v1/roles/**")
                    .hasAuthority("role:manage")
                    .requestMatchers(HttpMethod.POST, "/api/v1/roles/**")
                    .hasAuthority("role:manage")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**")
                    .hasAuthority("role:manage")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**")
                    .hasAuthority("role:manage")
                    .requestMatchers("/api/v1/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(basic -> basic.authenticationEntryPoint(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .formLogin(form -> form.disable())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, OAuth2AuthorizationRequestResolver authorizationRequestResolver,
      MongoUserDetailsService userDetailsService,
      GrantedAuthoritiesMapper oauthPermissionMapper,
      CurrentPermissionAuthorizationManager currentPermissionAuthorizationManager)
      throws Exception {
    return http
        .userDetailsService(userDetailsService)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/login", "/css/**")
                    .permitAll()
                    .requestMatchers("/", "/members/**")
                    .access(currentPermissionAuthorizationManager)
                    .anyRequest()
                    .authenticated())
        .formLogin(form -> form.loginPage("/login").permitAll())
        .oauth2Login(
            oauth2Login ->
                oauth2Login
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(oauthPermissionMapper))
                    .authorizationEndpoint(
                        authorization ->
                            authorization.authorizationRequestResolver(
                                authorizationRequestResolver)))
        .build();
  }

}
