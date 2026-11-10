package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Role;
import com.kitchensink.model.UserAccount;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for IdentityResolver and RolePermissionService.
 *
 * @author Deepak Bheemarasetty
 */
class IdentityAndPermissionServiceTest {

  private MongoTemplate mongoTemplate;
  private IdentityResolver identityResolver;
  private RolePermissionService rolePermissionService;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    identityResolver = new IdentityResolver(mongoTemplate);
    rolePermissionService = new RolePermissionService(mongoTemplate);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void identityResolverResolveIdentityFromOauth2() {
    OAuth2User principal = mock(OAuth2User.class);
    when(principal.getName()).thenReturn("oauth@example.com");
    when(principal.getAttribute("email")).thenReturn("oauth@example.com");
    OAuth2AuthenticationToken token =
        new OAuth2AuthenticationToken(principal, List.of(() -> "ROLE_USER"), "auth0");
    SecurityContextHolder.getContext().setAuthentication(token);

    assertThat(identityResolver.resolveIdentity(token)).isEqualTo("oauth@example.com");
  }

  @Test
  void identityResolverResolveIdentityFromJwt() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getClaimAsString("email")).thenReturn("jwt@example.com");
    JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, List.of(() -> "SCOPE_read"));
    SecurityContextHolder.getContext().setAuthentication(token);

    assertThat(identityResolver.resolveIdentity(token)).isEqualTo("jwt@example.com");
  }

  @Test
  void identityResolverFallbackToName() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getName()).thenReturn("fallback-user");

    assertThat(identityResolver.resolveIdentity(auth)).isEqualTo("fallback-user");
  }

  @Test
  void identityResolverRequireActorId() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getName()).thenReturn("registered@example.com");

    ObjectId actorId = new ObjectId();
    Document userDoc = new Document("_id", actorId);
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("users")))
        .thenReturn(userDoc);

    assertThat(identityResolver.requireActorId(auth)).isEqualTo(actorId);

    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(null);
    assertThatThrownBy(() -> identityResolver.requireActorId(auth))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rolePermissionServicePermissionsFor() {
    assertThat(rolePermissionService.permissionsFor(null)).isEmpty();
    assertThat(rolePermissionService.permissionsFor(List.of())).isEmpty();

    Role role = new Role();
    role.setName("admin");
    role.setDescription("admin");
    role.setPermissions(List.of("member:view", "member:create"));
    role.setUpdatedAt(java.time.Instant.now());

    when(mongoTemplate.find(any(Query.class), eq(Role.class))).thenReturn(List.of(role));

    Set<String> perms = rolePermissionService.permissionsFor(List.of("admin"));
    assertThat(perms).containsExactlyInAnyOrder("member:view", "member:create");
  }

  @Test
  void rolePermissionServiceUser() {
    assertThat(rolePermissionService.user(null).active()).isFalse();
    assertThat(rolePermissionService.user(" ").active()).isFalse();

    when(mongoTemplate.findOne(any(Query.class), eq(UserAccount.class))).thenReturn(null);
    assertThat(rolePermissionService.user("unknown").active()).isFalse();

    UserAccount user = new UserAccount();
    user.setUsername("active@example.com");
    user.setUsernameLower("active@example.com");
    user.setRoles(List.of("viewer"));
    user.setActive(false);
    when(mongoTemplate.findOne(any(Query.class), eq(UserAccount.class))).thenReturn(user);
    assertThat(rolePermissionService.user("active@example.com").active()).isFalse();

    user.setActive(true);
    user.setCreatedAt(java.time.Instant.now());
    user.setUpdatedAt(java.time.Instant.now());
    Role role = new Role();
    role.setName("viewer");
    role.setDescription("viewer");
    role.setPermissions(List.of("member:view"));
    role.setUpdatedAt(java.time.Instant.now());
    when(mongoTemplate.find(any(Query.class), eq(Role.class))).thenReturn(List.of(role));

    var userPerms = rolePermissionService.user("active@example.com");
    assertThat(userPerms.active()).isTrue();
    assertThat(userPerms.permissions()).contains("member:view");
  }
}
