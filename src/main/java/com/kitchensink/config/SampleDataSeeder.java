package com.kitchensink.config;

import com.kitchensink.model.Member;
import com.mongodb.bulk.BulkWriteResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Loads the sample member records on application startup without overwriting existing members. */
@Component
public class SampleDataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

  private final MongoTemplate mongoTemplate;
  private final Resource seedData;

  /** Creates a seeder for the configured sample data resource. */
  public SampleDataSeeder(
      MongoTemplate mongoTemplate,
      @org.springframework.beans.factory.annotation.Value("classpath:seed-members.json")
          Resource seedData) {
    this.mongoTemplate = mongoTemplate;
    this.seedData = seedData;
  }

  /** Loads missing sample members into MongoDB. */
  @Override
  public void run(ApplicationArguments args) throws IOException {
    String json;
    try (var inputStream = seedData.getInputStream()) {
      json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
    if (json.startsWith("\uFEFF")) {
      json = json.substring(1);
    }
    BsonArray members = BsonArray.parse(json);
    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkMode.UNORDERED, Member.class);
    for (BsonValue value : members) {
      BsonDocument member = value.asDocument();
      String email = member.getString("email").getValue();
      Query query = Query.query(Criteria.where("email").is(email));
      Update update =
          new Update()
              .setOnInsert("name", member.getString("name").getValue())
              .setOnInsert("email", email)
              .setOnInsert("phoneNumber", member.getString("phoneNumber").getValue());
      bulkOperations.upsert(query, update);
    }
    BulkWriteResult result = bulkOperations.execute();
    log.info(
        "Loaded {} sample members ({} newly inserted)",
        members.size(),
        result.getUpserts().size());
  }
}
