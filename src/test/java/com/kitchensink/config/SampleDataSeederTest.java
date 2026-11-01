package com.kitchensink.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Member;
import com.mongodb.bulk.BulkWriteResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** Tests startup loading of sample member data. */
class SampleDataSeederTest {
  @Test
  void upsertsSeedMembersByEmailWithoutReplacingExistingRecords() throws Exception {
    MongoTemplate mongoTemplate = Mockito.mock(MongoTemplate.class);
    BulkOperations bulkOperations = Mockito.mock(BulkOperations.class);
    BulkWriteResult result = Mockito.mock(BulkWriteResult.class);
    when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Member.class))
        .thenReturn(bulkOperations);
    when(bulkOperations.upsert(any(Query.class), any(Update.class))).thenReturn(bulkOperations);
    when(bulkOperations.execute()).thenReturn(result);
    when(result.getUpserts()).thenReturn(List.of());
    String json =
        "\uFEFF"
            + """
        [
          {"name":"Ada Example","email":"ada@example.com","phoneNumber":"2125551001"},
          {"name":"Sam Example","email":"sam@example.com","phoneNumber":"2125551002"}
        ]
        """;
    SampleDataSeeder seeder =
        new SampleDataSeeder(
            mongoTemplate,
            new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));

    seeder.run(new DefaultApplicationArguments(new String[0]));
    new SampleDataSeeder(
            mongoTemplate,
            new ByteArrayResource(json.substring(1).getBytes(StandardCharsets.UTF_8)))
        .run(new DefaultApplicationArguments(new String[0]));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(bulkOperations, times(4)).upsert(queryCaptor.capture(), updateCaptor.capture());
    assertThat(queryCaptor.getAllValues().getFirst().getQueryObject().getString("email"))
        .isEqualTo("ada@example.com");
    assertThat(
            updateCaptor
                .getAllValues()
                .getFirst()
                .getUpdateObject()
                .get("$setOnInsert", Document.class)
                .getString("email"))
        .isEqualTo("ada@example.com");
    verify(bulkOperations, times(2)).execute();
  }
}
