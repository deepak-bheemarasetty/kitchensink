package com.kitchensink.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.model.Member;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/** Tests MongoDB document mapping without requiring a running MongoDB server. */
class MemberMappingTest {
  @Test
  void memberMapsToMembersCollectionWithUniqueEmailIndex() throws Exception {
    MongoMappingContext context = new MongoMappingContext();
    context.setInitialEntitySet(Set.of(Member.class));
    context.afterPropertiesSet();

    var entity = context.getRequiredPersistentEntity(Member.class);
    var email = entity.getRequiredPersistentProperty("email");

    assertThat(entity.getCollection()).isEqualTo("members");
    assertThat(entity.getRequiredIdProperty().getName()).isEqualTo("id");
    assertThat(email.findAnnotation(Indexed.class)).isNotNull();
    assertThat(email.findAnnotation(Indexed.class).unique()).isTrue();
  }
}
