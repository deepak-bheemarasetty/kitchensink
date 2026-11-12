package com.kitchensink.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for domain models and entity validation.
 *
 * @author Deepak Bheemarasetty
 */
class ModelTest {

  @Test
  void memberCreateAndSetters() {
    Member member = Member.create();
    ObjectId id = new ObjectId();
    ObjectId actorId = new ObjectId();
    Instant now = Instant.now();

    member.setId(id);
    member.setMemberId("M-12345");
    member.setFirstName("John");
    member.setLastName("Doe");
    member.setEmail("john.doe@example.com");
    member.setEmailLower("john.doe@example.com");
    member.setFullNameLower("john doe");
    member.setCountryCode("1");
    member.setPhone("2125551212");
    member.setStatus("active");
    member.setCreatedAt(now);
    member.setCreatedBy(actorId);
    member.setUpdatedAt(now);
    member.setUpdatedBy(actorId);
    member.setVersion(1);

    assertThat(member.getId()).isEqualTo(id);
    assertThat(member.getMemberId()).isEqualTo("M-12345");
    assertThat(member.getFirstName()).isEqualTo("John");
    assertThat(member.getLastName()).isEqualTo("Doe");
    assertThat(member.getEmail()).isEqualTo("john.doe@example.com");
    assertThat(member.getEmailLower()).isEqualTo("john.doe@example.com");
    assertThat(member.getFullNameLower()).isEqualTo("john doe");
    assertThat(member.getCountryCode()).isEqualTo("1");
    assertThat(member.getPhone()).isEqualTo("2125551212");
    assertThat(member.getStatus()).isEqualTo("active");
    assertThat(member.getCreatedAt()).isEqualTo(now);
    assertThat(member.getCreatedBy()).isEqualTo(actorId);
    assertThat(member.getUpdatedAt()).isEqualTo(now);
    assertThat(member.getUpdatedBy()).isEqualTo(actorId);
    assertThat(member.getVersion()).isEqualTo(1);
    assertThat(member.getDeletedAt()).isNull();
    assertThat(member.getDeletedBy()).isNull();
    assertThat(member.getFullName()).isEqualTo("John Doe");

    Instant delDate = Instant.now();
    member.setDeletedAt(delDate);
    member.setDeletedBy(actorId);
    assertThat(member.getDeletedAt()).isEqualTo(delDate);
    assertThat(member.getDeletedBy()).isEqualTo(actorId);

    member.validate();
    member.setLastName(null);
    assertThat(member.getFullName()).isEqualTo("John");
    member.setEmail(null);
    assertThat(member.getEmailLower()).isNull();
  }

  @Test
  void roleModelAndValidation() {
    Role role = new Role();
    ObjectId id = new ObjectId();
    role.setId(id);
    role.setName("manager");
    role.setDescription("manager role");
    role.setPermissions(java.util.List.of("member:view", "member:create"));
    role.setSystem(false);
    Instant now = Instant.now();
    role.setUpdatedAt(now);

    assertThat(role.getId()).isEqualTo(id);
    assertThat(role.getName()).isEqualTo("manager");
    assertThat(role.getDescription()).isEqualTo("manager role");
    assertThat(role.getPermissions()).containsExactly("member:view", "member:create");
    assertThat(role.isSystem()).isFalse();
    assertThat(role.getUpdatedAt()).isEqualTo(now);

    role.validate();
  }

  @Test
  void userAccountModelAndValidation() {
    UserAccount account = new UserAccount();
    ObjectId id = new ObjectId();
    Instant now = Instant.now();

    account.setId(id);
    account.setUsername("user@example.com");
    account.setUsernameLower("user@example.com");
    account.setRoles(java.util.List.of("viewer"));
    account.setActive(true);
    account.setCreatedAt(now);
    account.setUpdatedAt(now);
    account.setLastLoginAt(now);

    assertThat(account.getId()).isEqualTo(id);
    assertThat(account.getUsername()).isEqualTo("user@example.com");
    assertThat(account.getUsernameLower()).isEqualTo("user@example.com");
    assertThat(account.getRoles()).containsExactly("viewer");
    assertThat(account.isActive()).isTrue();
    assertThat(account.getCreatedAt()).isEqualTo(now);
    assertThat(account.getUpdatedAt()).isEqualTo(now);
    assertThat(account.getLastLoginAt()).isEqualTo(now);
    account.validate();
    account.setRoles(null);
    assertThat(account.getRoles()).isNull();
  }

  @Test
  void auditLogModelAndValidation() {
    AuditLog log = new AuditLog();
    ObjectId actorId = new ObjectId();
    ObjectId entityId = new ObjectId();
    Instant now = Instant.now();

    log.setAt(now);
    log.setActorId(actorId);
    log.setAction("member.create");
    log.setEntityType("member");
    log.setEntityId(entityId);
    log.setChanges(java.util.Map.of("name", "John"));
    log.setRequestId("req-123");
    log.setIp("127.0.0.1");

    assertThat(log.getId()).isNull();
    assertThat(log.getAt()).isEqualTo(now);
    assertThat(log.getActorId()).isEqualTo(actorId);
    assertThat(log.getAction()).isEqualTo("member.create");
    assertThat(log.getEntityType()).isEqualTo("member");
    assertThat(log.getEntityId()).isEqualTo(entityId);
    assertThat(log.getChanges()).containsEntry("name", "John");
    assertThat(log.getRequestId()).isEqualTo("req-123");
    assertThat(log.getIp()).isEqualTo("127.0.0.1");

    log.validateForInsert();

    ObjectId id = new ObjectId();
    log.setId(id);
    assertThat(log.getId()).isEqualTo(id);
  }
}
