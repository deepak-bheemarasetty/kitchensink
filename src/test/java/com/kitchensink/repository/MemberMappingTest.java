package com.kitchensink.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.model.Member;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/** Tests MongoDB document mapping without requiring a running MongoDB server. */
class MemberMappingTest {
  @Test
  void memberCanBeCreatedWithPrivateConstructorFactoryAndStoresAllFields() {
    Member member = Member.create();
    Date timestamp = new Date();
    member.setId(1L);
    member.setName("Jane Doe");
    member.setEmail("jane@example.com");
    member.setPhoneNumber("001234");
    member.setCountryCode(1);
    member.setCreatedBy("user");
    member.setCreationDate(timestamp);
    member.setLastUpdatedBy("user");
    member.setLastUpdateDate(timestamp);
    member.setVersion(0L);

    assertThat(member.getId()).isEqualTo(1L);
    assertThat(member.getName()).isEqualTo("Jane Doe");
    assertThat(member.getEmail()).isEqualTo("jane@example.com");
    assertThat(member.getPhoneNumber()).isEqualTo("001234");
    assertThat(member.getCountryCode()).isEqualTo(1);
    assertThat(member.getCreatedBy()).isEqualTo("user");
    assertThat(member.getCreationDate()).isEqualTo(timestamp);
    assertThat(member.getLastUpdatedBy()).isEqualTo("user");
    assertThat(member.getLastUpdateDate()).isEqualTo(timestamp);
    assertThat(member.getVersion()).isZero();
  }

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
    assertThat(Member.class.getAnnotation(CompoundIndex.class)).isNull();
    assertThat(entity.getRequiredPersistentProperty("countryCode")).isNotNull();
    assertThat(entity.getRequiredPersistentProperty("createdBy")).isNotNull();
    assertThat(entity.getRequiredPersistentProperty("creationDate")).isNotNull();
    assertThat(entity.getRequiredPersistentProperty("lastUpdatedBy")).isNotNull();
    assertThat(entity.getRequiredPersistentProperty("lastUpdateDate")).isNotNull();
    assertThat(entity.getRequiredPersistentProperty("version")).isNotNull();
    assertThat(Member.class.getDeclaredConstructor().canAccess(null)).isFalse();
  }
}
