package com.kitchensink.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Configures MongoDB schema constraints and auditing metadata. */
@Configuration
@EnableMongoAuditing
public class MongoConfiguration {

  @Bean
  AuditorAware<String> auditorAware() {
    return () ->
        Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName);
  }

  /* Phase 2: validators and indexes are installed by versioned migration scripts.
  @Bean
  ApplicationRunner memberCollectionSchema(MongoTemplate mongoTemplate) {
    return args -> {
      var database = mongoTemplate.getDb();
      Document validator = memberValidator();
      List<String> collectionNames =
          database.listCollectionNames().into(new java.util.ArrayList<>());
      if (!collectionNames.contains(MEMBER_COLLECTION)) {
        database.createCollection(MEMBER_COLLECTION);
      }
      database.runCommand(
          new Document("collMod", MEMBER_COLLECTION)
              .append("validator", validator)
              .append("validationLevel", "strict"));
    };
  }

  private org.bson.Document memberValidator() {
    Document properties =
        new Document("_id", new Document("bsonType", "long"))
            .append("name", new Document("bsonType", "string").append("maxLength", 40))
            .append(
                "email",
                new Document("bsonType", "string")
                    .append("maxLength", 40)
                    .append("pattern", "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            .append(
                "phoneNumber",
                new Document("bsonType", "string").append("pattern", "^[0-9]{4,15}$"))
            .append(
                "countryCode",
                new Document("bsonType", "int").append("minimum", 1).append("maximum", 9999))
            .append("createdBy", new Document("bsonType", "string").append("maxLength", 40))
            .append("creationDate", new Document("bsonType", "date"))
            .append("lastUpdatedBy", new Document("bsonType", "string").append("maxLength", 40))
            .append("lastUpdateDate", new Document("bsonType", "date"))
            .append("version", new Document("bsonType", List.of("long", "int")));
    return new Document(
        "$jsonSchema",
        new Document(
                "required",
                List.of(
                    "_id",
                    "name",
                    "email",
                    "phoneNumber",
                    "countryCode",
                    "createdBy",
                    "creationDate",
                    "lastUpdatedBy",
                    "lastUpdateDate",
                    "version"))
            .append("bsonType", "object")
            .append("properties", properties));
  } */
}
