package com.kitchensink.config;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Automatically initializes standard application roles and configured administrator user accounts
 * in MongoDB on application startup if they are not already present.
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Order(2)
public class DatabaseDataInitializer implements ApplicationRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseDataInitializer.class);

  private final MongoTemplate mongoTemplate;
  private final List<String> adminEmails;

  public DatabaseDataInitializer(
      MongoTemplate mongoTemplate,
      @Value("${app.security.admin-emails:}") List<String> adminEmails) {
    this.mongoTemplate = mongoTemplate;
    this.adminEmails = adminEmails == null ? List.of() : adminEmails;
  }

  @Override
  public void run(ApplicationArguments args) {
    seedRoles();
    seedAdminUsers();
  }

  private void seedRoles() {
    record RoleDef(String name, String description, boolean isSystem, List<String> permissions) {}

    List<RoleDef> defaultRoles =
        List.of(
            new RoleDef("viewer", "viewer role", true, List.of("member:view")),
            new RoleDef("creator", "creator role", false, List.of("member:view", "member:create")),
            new RoleDef("editor", "editor role", false, List.of("member:view", "member:update")),
            new RoleDef("deleter", "deleter role", false, List.of("member:view", "member:delete")),
            new RoleDef(
                "manager",
                "manager role",
                false,
                List.of("member:view", "member:create", "member:update", "member:delete")),
            new RoleDef(
                "admin",
                "admin role",
                true,
                List.of(
                    "member:view",
                    "member:create",
                    "member:update",
                    "member:delete",
                    "user:manage",
                    "role:manage")));

    Date now = new Date();
    for (RoleDef role : defaultRoles) {
      Query query = Query.query(Criteria.where("name").is(role.name()));
      Update update =
          new Update()
              .setOnInsert("name", role.name())
              .setOnInsert("description", role.description())
              .setOnInsert("isSystem", role.isSystem())
              .setOnInsert("permissions", role.permissions())
              .setOnInsert("updatedAt", now);

      mongoTemplate.upsert(query, update, "roles");
    }
    LOGGER.info("Initialized {} application roles in MongoDB.", defaultRoles.size());
  }

  private void seedAdminUsers() {
    if (adminEmails.isEmpty()) {
      LOGGER.info("No administrator user accounts configured in app.security.admin-emails.");
      return;
    }

    Date now = new Date();
    int count = 0;
    for (String rawEmail : adminEmails) {
      if (rawEmail == null || rawEmail.isBlank()) {
        continue;
      }
      String email = rawEmail.trim();
      String emailLower = email.toLowerCase(Locale.ROOT);
      Query query = Query.query(Criteria.where("usernameLower").is(emailLower));
      Update update =
          new Update()
              .setOnInsert("username", email)
              .setOnInsert("usernameLower", emailLower)
              .setOnInsert("roles", List.of("admin"))
              .setOnInsert("isActive", true)
              .setOnInsert("createdAt", now)
              .setOnInsert("updatedAt", now)
              .setOnInsert("lastLoginAt", null);

      mongoTemplate.upsert(query, update, "users");
      count++;
    }
    LOGGER.info("Initialized {} administrator user accounts in MongoDB.", count);
  }
}
