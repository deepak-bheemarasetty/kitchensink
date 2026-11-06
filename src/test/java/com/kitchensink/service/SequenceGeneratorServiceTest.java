package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class SequenceGeneratorServiceTest {
  @Mock private MongoTemplate mongoTemplate;

  @Test
  void obtainsNextLongFromAtomicMongoCounter() {
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Document.class),
            eq("sequences")))
        .thenReturn(new Document("sequence", 41L));

    assertThat(new SequenceGeneratorService(mongoTemplate).nextMemberId()).isEqualTo(41L);

    verify(mongoTemplate)
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Document.class),
            eq("sequences"));
  }
}
