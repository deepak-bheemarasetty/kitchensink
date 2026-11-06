package com.kitchensink.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds local RBAC test users only when explicitly enabled. */
@Component
@Profile("local")
public class LocalTestDataSeeder implements ApplicationRunner {
  private static final List<Document> ROLES = List.of(
      role("viewer", List.of("member:view")),
      role("creator", List.of("member:view", "member:create")),
      role("editor", List.of("member:view", "member:update")),
      role("deleter", List.of("member:view", "member:delete")),
      role("manager", List.of("member:view", "member:create", "member:update", "member:delete")),
      role("admin", List.of("member:view", "member:create", "member:update", "member:delete", "user:manage", "role:manage")));

  private final MongoTemplate mongoTemplate;
  private final boolean enabled;
  private final Path credentialsFile;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public LocalTestDataSeeder(
      MongoTemplate mongoTemplate,
      @Value("${app.local.seed.enabled:false}") boolean enabled,
      @Value("${app.local.seed.credentials-file:test-user-credentials.txt}") Path credentialsFile) {
    this.mongoTemplate = mongoTemplate;
    this.enabled = enabled;
    this.credentialsFile = credentialsFile;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    if (!enabled) return;
    Date now = Date.from(Instant.now());
    for (Document role : ROLES) {
      mongoTemplate.upsert(
          Query.query(Criteria.where("name").is(role.getString("name"))),
          new Update().setOnInsert("name", role.getString("name"))
              .setOnInsert("description", role.getString("description"))
              .setOnInsert("permissions", role.getList("permissions", String.class))
              .setOnInsert("isSystem", role.getBoolean("isSystem"))
              .set("updatedAt", now),
          "roles");
    }
    for (String line : Files.readAllLines(credentialsFile)) {
      if (line.isBlank() || line.startsWith("#")) continue;
      String[] credential = line.split("=", 2);
      if (credential.length != 2 || credential[1].length() < 12) {
        throw new IllegalArgumentException("Invalid local seed credential entry");
      }
      String username = credential[0].trim();
      String role = username.substring("kitchensink.".length(), username.indexOf('@'));
      mongoTemplate.upsert(
          Query.query(Criteria.where("usernameLower").is(username.toLowerCase(Locale.ROOT))),
          new Update().setOnInsert("username", username)
              .setOnInsert("usernameLower", username.toLowerCase(Locale.ROOT))
              .set("passwordHash", passwordEncoder.encode(credential[1]))
              .setOnInsert("roles", List.of(role))
              .setOnInsert("isActive", true)
              .setOnInsert("createdAt", now)
              .set("updatedAt", now),
          "users");
    }
  }

  private static Document role(String name, List<String> permissions) {
    return new Document("name", name)
        .append("description", name + " role")
        .append("permissions", permissions)
        .append("isSystem", name.equals("admin") || name.equals("viewer"));
  }
}
