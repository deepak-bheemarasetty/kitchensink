package com.kitchensink.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for domain validation rules.
 *
 * @author Deepak Bheemarasetty
 */
class ValidatorTest {

  @Test
  void firstNameValidation() {
    assertThat(Validator.firstName("John")).isEqualTo("John");
    assertThatThrownBy(() -> Validator.firstName(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.firstName("   ")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.firstName("a".repeat(61)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.firstName("John123"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void lastNameValidation() {
    assertThat(Validator.lastName(null)).isNull();
    assertThat(Validator.lastName("")).isNull();
    assertThat(Validator.lastName("  ")).isNull();
    assertThat(Validator.lastName("Smith")).isEqualTo("Smith");
    assertThatThrownBy(() -> Validator.lastName("a".repeat(61)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.lastName("Smith99")).isInstanceOf(ValidationException.class);
  }

  @Test
  void emailValidation() {
    assertThat(Validator.email("test@example.com")).isEqualTo("test@example.com");
    assertThatThrownBy(() -> Validator.email(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.email("bad-email")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.email("a".repeat(250) + "@b.com"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void phoneValidation() {
    assertThat(Validator.phone("2125551212")).isEqualTo("2125551212");
    assertThatThrownBy(() -> Validator.phone(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.phone("0123456")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.phone("123")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.phone("123456789012345"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.phone("abcd12345")).isInstanceOf(ValidationException.class);
  }

  @Test
  void countryCodeValidation() {
    assertThat(Validator.countryCode(null)).isNull();
    assertThat(Validator.countryCode("")).isNull();
    assertThat(Validator.countryCode("   ")).isNull();
    assertThat(Validator.countryCode("1")).isEqualTo("1");
    assertThat(Validator.countryCode("91")).isEqualTo("91");
    assertThat(Validator.countryCode("850")).isEqualTo("850");
    assertThatThrownBy(() -> Validator.countryCode("12345"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.countryCode("abc")).isInstanceOf(ValidationException.class);
  }

  @Test
  void memberIdValidation() {
    assertThat(Validator.memberId("M-12345")).isEqualTo("M-12345");
    assertThatThrownBy(() -> Validator.memberId(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.memberId("   ")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.memberId("A".repeat(31)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void statusValidation() {
    assertThat(Validator.status("active")).isEqualTo("active");
    assertThat(Validator.status("inactive")).isEqualTo("inactive");
    assertThatThrownBy(() -> Validator.status(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.status("pending")).isInstanceOf(ValidationException.class);
  }

  @Test
  void versionValidation() {
    assertThat(Validator.version(1)).isEqualTo(1);
    assertThatThrownBy(() -> Validator.version(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.version(0)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.version(-1)).isInstanceOf(ValidationException.class);
  }

  @Test
  void roleNameValidation() {
    assertThat(Validator.roleName("viewer")).isEqualTo("viewer");
    assertThatThrownBy(() -> Validator.roleName(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roleName("a")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roleName("Invalid Role!"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roleName("a".repeat(31)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void roleDescriptionValidation() {
    assertThat(Validator.roleDescription("desc")).isEqualTo("desc");
    assertThat(Validator.roleDescription("  ")).isEqualTo("");
    assertThatThrownBy(() -> Validator.roleDescription(null))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roleDescription("a".repeat(201)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rolesListValidation() {
    assertThat(Validator.roles(List.of("viewer", "admin"))).containsExactly("viewer", "admin");
    assertThatThrownBy(() -> Validator.roles(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roles(List.of())).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roles(List.of("admin", "admin")))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
            () -> Validator.roles(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void usernameValidation() {
    assertThat(Validator.username("user@example.com")).isEqualTo("user@example.com");
    assertThatThrownBy(() -> Validator.username(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.username("bad")).isInstanceOf(ValidationException.class);
  }

  @Test
  void searchQueryValidation() {
    assertThat(Validator.searchQuery(null)).isNull();
    assertThat(Validator.searchQuery("   ")).isEqualTo("   ");
    assertThat(Validator.searchQuery("query")).isEqualTo("query");
    assertThatThrownBy(() -> Validator.searchQuery("a".repeat(101)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.searchQuery("bad\0query"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void sortFieldValidation() {
    assertThat(Validator.sortField("email")).isEqualTo("email");
    assertThat(Validator.sortField("name")).isEqualTo("name");
    assertThatThrownBy(() -> Validator.sortField(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.sortField("")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.sortField("unknown"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void pageLimitValidation() {
    assertThat(Validator.pageLimit(1)).isEqualTo(1);
    assertThat(Validator.pageLimit(25)).isEqualTo(25);
    assertThatThrownBy(() -> Validator.pageLimit(0)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.pageLimit(-5)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.pageLimit(101)).isInstanceOf(ValidationException.class);
  }

  @Test
  void cursorValidation() {
    assertThat(Validator.cursor(null)).isNull();
    assertThat(Validator.cursor("   ")).isEqualTo("   ");
    assertThat(Validator.cursor("abc")).isEqualTo("abc");
    assertThatThrownBy(() -> Validator.cursor("a".repeat(257)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void idempotencyKeyValidation() {
    assertThat(Validator.idempotencyKey("valid-key-123")).isEqualTo("valid-key-123");
    assertThatThrownBy(() -> Validator.idempotencyKey(null))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.idempotencyKey("   "))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.idempotencyKey("a".repeat(129)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.idempotencyKey("bad\0key"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void entityValidationMethods() {
    com.kitchensink.model.Member member = com.kitchensink.model.Member.create();
    member.setMemberId("M-1234567890ABCDEF");
    member.setFirstName("John");
    member.setLastName("Doe");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");
    member.setCountryCode("1");
    member.setStatus("active");
    member.setVersion(1);
    member.setCreatedAt(java.time.Instant.now());
    member.setUpdatedAt(java.time.Instant.now());
    member.setCreatedBy(new ObjectId());
    member.setUpdatedBy(new ObjectId());
    Validator.member(member);

    com.kitchensink.model.Role role = new com.kitchensink.model.Role();
    role.setName("viewer");
    role.setDescription("viewer");
    role.setPermissions(List.of("member:view"));
    role.setUpdatedAt(java.time.Instant.now());
    Validator.role(role);

    com.kitchensink.model.UserAccount user = new com.kitchensink.model.UserAccount();
    user.setUsername("user@example.com");
    user.setUsernameLower("user@example.com");
    user.setRoles(List.of("viewer"));
    user.setCreatedAt(java.time.Instant.now());
    user.setUpdatedAt(java.time.Instant.now());
    Validator.userAccount(user);

    com.kitchensink.model.AuditLog log = new com.kitchensink.model.AuditLog();
    log.setAt(java.time.Instant.now());
    log.setActorId(new ObjectId());
    log.setAction("member.create");
    log.setEntityType("member");
    log.setEntityId(new ObjectId());
    log.setChanges(java.util.Map.of("k", "v"));
    log.setRequestId("req-1");
    Validator.auditLog(log);
  }
}
