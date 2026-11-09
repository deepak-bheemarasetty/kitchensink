package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests Mongo configuration components and auditing resolver.
 *
 * @author Deepak Bheemarasetty
 */
class MongoConfigurationTest {
  private final MongoConfiguration configuration = new MongoConfiguration();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void auditorUsesAuthenticatedPrincipalAndSkipsAnonymousUsers() {
    var auditor = configuration.auditorAware();
    assertThat(auditor.getCurrentAuditor()).isEmpty();

    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("alice", "password", "ROLE_USER"));
    assertThat(auditor.getCurrentAuditor()).contains("alice");

    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }

  @Test
  void mappingMongoConverterAndTransactionManagerInstantiation() {
    MongoDatabaseFactory factory = org.mockito.Mockito.mock(MongoDatabaseFactory.class);
    org.mockito.Mockito.doReturn(
            new org.springframework.data.mongodb.core.MongoExceptionTranslator())
        .when(factory)
        .getExceptionTranslator();
    MongoMappingContext mappingContext = new MongoMappingContext();
    MongoCustomConversions conversions =
        new MongoCustomConversions(java.util.Collections.emptyList());

    var converter = configuration.mappingMongoConverter(factory, mappingContext, conversions);
    assertThat(converter).isNotNull();

    var transactionManager = configuration.mongoTransactionManager(factory);
    assertThat(transactionManager).isNotNull();
  }
}
