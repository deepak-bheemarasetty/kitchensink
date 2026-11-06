package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoDatabase;
import java.util.Collection;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
  void createsMemberCollectionAndInstallsMongoSchema() throws Exception {
    MongoTemplate template = mock(MongoTemplate.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    ListCollectionNamesIterable collectionNames = mock(ListCollectionNamesIterable.class);
    when(template.getDb()).thenReturn(database);
    when(database.listCollectionNames()).thenReturn(collectionNames);
    when(collectionNames.into(any(Collection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Collection.class));

    ApplicationRunner runner = configuration.memberCollectionSchema(template);
    runner.run(mock(ApplicationArguments.class));

    verify(database).createCollection("members");
    var commandCaptor = org.mockito.ArgumentCaptor.forClass(Document.class);
    verify(database).runCommand(commandCaptor.capture());
    Document command = commandCaptor.getValue();
    assertThat(command.getString("collMod")).isEqualTo("members");
    assertThat(command.getString("validationLevel")).isEqualTo("strict");
    Document schema = (Document) command.get("validator", Document.class).get("$jsonSchema");
    assertThat(schema.getList("required", String.class))
        .contains("_id", "name", "email", "phoneNumber", "countryCode", "version");
    Document properties = schema.get("properties", Document.class);
    assertThat(properties.get("email", Document.class).getInteger("maxLength")).isEqualTo(40);
    assertThat(properties.get("phoneNumber", Document.class).getString("pattern"))
        .isEqualTo("^[0-9]{4,15}$");
  }
}
