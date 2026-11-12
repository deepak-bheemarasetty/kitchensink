package com.kitchensink.config;

import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Automatically applies MongoDB collection schema validators and performance/unique indexes on
 * startup, guaranteeing that new and existing databases enforce data integrity and partial indexes
 * without relying on external migration scripts.
 *
 * @author Deepak Bheemarasetty
 */
@Component
@Order(1)
public class DatabaseSchemaInitializer implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

  private final MongoTemplate mongoTemplate;

  public DatabaseSchemaInitializer(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      initializeMembersSchemaAndIndexes();
      initializeUsersSchemaAndIndexes();
      initializeRolesSchemaAndIndexes();
      initializeAuditLogsSchemaAndIndexes();
      LOGGER.info("Initialized MongoDB collection schema validators and indexes successfully.");
    } catch (Exception ex) {
      LOGGER.warn(
          "Failed to complete MongoDB schema and index initialization: {}", ex.getMessage());
    }
  }

  private void initializeMembersSchemaAndIndexes() {
    ensureCollection("members");

    Document membersSchema =
        new Document(
            "$jsonSchema",
            new Document("bsonType", "object")
                .append(
                    "required",
                    List.of(
                        "_id",
                        "memberId",
                        "firstName",
                        "lastName",
                        "fullNameLower",
                        "email",
                        "emailLower",
                        "phone",
                        "status",
                        "createdAt",
                        "updatedAt",
                        "version"))
                .append("additionalProperties", false)
                .append(
                    "properties",
                    new Document("_id", new Document("bsonType", "objectId"))
                        .append(
                            "memberId",
                            new Document("bsonType", "string")
                                .append("minLength", 3)
                                .append("maxLength", 20)
                                .append("pattern", "^[A-Z0-9-]+$"))
                        .append(
                            "firstName",
                            new Document("bsonType", "string")
                                .append("minLength", 1)
                                .append("maxLength", 60))
                        .append(
                            "lastName", new Document("bsonType", "string").append("maxLength", 60))
                        .append(
                            "fullNameLower",
                            new Document("bsonType", "string").append("maxLength", 121))
                        .append(
                            "email",
                            new Document("bsonType", "string")
                                .append("minLength", 3)
                                .append("maxLength", 254))
                        .append(
                            "emailLower",
                            new Document("bsonType", "string")
                                .append("minLength", 3)
                                .append("maxLength", 254))
                        .append(
                            "countryCode",
                            new Document("bsonType", List.of("string", "null"))
                                .append("pattern", "^[1-9][0-9]{0,3}$"))
                        .append(
                            "phone",
                            new Document("bsonType", "string")
                                .append("pattern", "^[1-9][0-9]{5,13}$"))
                        .append(
                            "status",
                            new Document("bsonType", "string")
                                .append("enum", List.of("active", "inactive")))
                        .append("createdAt", new Document("bsonType", "date"))
                        .append("createdBy", new Document("bsonType", List.of("objectId", "null")))
                        .append("updatedAt", new Document("bsonType", "date"))
                        .append("updatedBy", new Document("bsonType", List.of("objectId", "null")))
                        .append("version", new Document("bsonType", "int").append("minimum", 1))
                        .append("deletedAt", new Document("bsonType", List.of("date", "null")))
                        .append(
                            "deletedBy", new Document("bsonType", List.of("objectId", "null")))));

    applyValidator("members", membersSchema, "moderate", "error");

    // Partial unique indexes for active members (deletedAt == null)
    mongoTemplate
        .indexOps("members")
        .createIndex(
            new Index()
                .on("emailLower", Sort.Direction.ASC)
                .named("uq_members_email_active")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("deletedAt").is(null))));

    mongoTemplate
        .indexOps("members")
        .createIndex(
            new Index()
                .on("memberId", Sort.Direction.ASC)
                .named("uq_members_member_id_active")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("deletedAt").is(null))));

    mongoTemplate
        .indexOps("members")
        .createIndex(
            new Index()
                .on("fullNameLower", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC)
                .named("ix_members_name_id"));

    mongoTemplate
        .indexOps("members")
        .createIndex(
            new Index()
                .on("status", Sort.Direction.ASC)
                .on("fullNameLower", Sort.Direction.ASC)
                .named("ix_members_status_name"));
  }

  private void initializeUsersSchemaAndIndexes() {
    ensureCollection("users");

    Document usersSchema =
        new Document(
            "$jsonSchema",
            new Document("bsonType", "object")
                .append(
                    "required",
                    List.of(
                        "username", "usernameLower", "roles", "isActive", "createdAt", "updatedAt"))
                .append("additionalProperties", false)
                .append(
                    "properties",
                    new Document("_id", new Document("bsonType", "objectId"))
                        .append(
                            "username",
                            new Document("bsonType", "string")
                                .append("minLength", 3)
                                .append("maxLength", 254)
                                .append("pattern", "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
                        .append(
                            "usernameLower",
                            new Document("bsonType", "string")
                                .append("minLength", 3)
                                .append("maxLength", 254)
                                .append("pattern", "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
                        .append(
                            "roles",
                            new Document("bsonType", "array")
                                .append("minItems", 1)
                                .append("maxItems", 10)
                                .append("uniqueItems", true)
                                .append("items", new Document("bsonType", "string")))
                        .append("isActive", new Document("bsonType", "bool"))
                        .append("createdAt", new Document("bsonType", "date"))
                        .append("updatedAt", new Document("bsonType", "date"))
                        .append("lastLoginAt", new Document("bsonType", List.of("date", "null")))));

    applyValidator("users", usersSchema, "strict", "error");

    mongoTemplate
        .indexOps("users")
        .createIndex(
            new Index()
                .on("usernameLower", Sort.Direction.ASC)
                .named("uq_users_username_lower")
                .unique());
  }

  private void initializeRolesSchemaAndIndexes() {
    ensureCollection("roles");

    Document rolesSchema =
        new Document(
            "$jsonSchema",
            new Document("bsonType", "object")
                .append(
                    "required",
                    List.of("name", "description", "permissions", "isSystem", "updatedAt"))
                .append("additionalProperties", false)
                .append(
                    "properties",
                    new Document("_id", new Document("bsonType", "objectId"))
                        .append(
                            "name",
                            new Document("bsonType", "string")
                                .append("minLength", 2)
                                .append("maxLength", 30)
                                .append("pattern", "^[a-z0-9 -]+$"))
                        .append(
                            "description",
                            new Document("bsonType", "string").append("maxLength", 200))
                        .append(
                            "permissions",
                            new Document("bsonType", "array")
                                .append("minItems", 1)
                                .append("uniqueItems", true)
                                .append(
                                    "items",
                                    new Document(
                                        "enum",
                                        List.of(
                                            "member:view",
                                            "member:create",
                                            "member:update",
                                            "member:delete",
                                            "user:manage",
                                            "role:manage"))))
                        .append("isSystem", new Document("bsonType", "bool"))
                        .append("updatedAt", new Document("bsonType", "date"))));

    applyValidator("roles", rolesSchema, "strict", "error");

    mongoTemplate
        .indexOps("roles")
        .createIndex(new Index().on("name", Sort.Direction.ASC).named("uq_roles_name").unique());
  }

  private void initializeAuditLogsSchemaAndIndexes() {
    ensureCollection("audit_logs");

    Document auditLogsSchema =
        new Document(
            "$jsonSchema",
            new Document("bsonType", "object")
                .append(
                    "required",
                    List.of(
                        "at",
                        "actorId",
                        "action",
                        "entityType",
                        "entityId",
                        "changes",
                        "requestId"))
                .append("additionalProperties", false)
                .append(
                    "properties",
                    new Document("_id", new Document("bsonType", "objectId"))
                        .append("at", new Document("bsonType", "date"))
                        .append("actorId", new Document("bsonType", "objectId"))
                        .append(
                            "action",
                            new Document("bsonType", "string")
                                .append(
                                    "enum",
                                    List.of(
                                        "member.create",
                                        "member.update",
                                        "member.delete",
                                        "member.restore",
                                        "user.roleChange",
                                        "role.update",
                                        "auth.loginFailed")))
                        .append("entityType", new Document("bsonType", "string"))
                        .append("entityId", new Document("bsonType", "objectId"))
                        .append("changes", new Document("bsonType", "object"))
                        .append(
                            "requestId", new Document("bsonType", "string").append("minLength", 1))
                        .append("ip", new Document("bsonType", List.of("string", "null")))));

    applyValidator("audit_logs", auditLogsSchema, "strict", "error");

    mongoTemplate
        .indexOps("audit_logs")
        .createIndex(
            new Index()
                .on("entityId", Sort.Direction.ASC)
                .on("at", Sort.Direction.DESC)
                .named("ix_audit_entity_at"));

    mongoTemplate
        .indexOps("audit_logs")
        .createIndex(
            new Index()
                .on("actorId", Sort.Direction.ASC)
                .on("at", Sort.Direction.DESC)
                .named("ix_audit_actor_at"));
  }

  private void ensureCollection(String collectionName) {
    if (!mongoTemplate.collectionExists(collectionName)) {
      mongoTemplate.createCollection(collectionName);
    }
  }

  private void applyValidator(
      String collectionName,
      Document validatorDoc,
      String validationLevel,
      String validationAction) {
    Document collModCmd =
        new Document("collMod", collectionName)
            .append("validator", validatorDoc)
            .append("validationLevel", validationLevel)
            .append("validationAction", validationAction);
    mongoTemplate.executeCommand(collModCmd);
  }
}
