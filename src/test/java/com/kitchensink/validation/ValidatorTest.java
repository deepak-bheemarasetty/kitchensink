package com.kitchensink.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
    assertThatThrownBy(() -> Validator.firstName(null))
        .isInstanceOf(ValidationException.class)
        .hasMessage("First Name is required");
    assertThatThrownBy(() -> Validator.firstName("   "))
        .isInstanceOf(ValidationException.class)
        .hasMessage("First Name is required");
    assertThatThrownBy(() -> Validator.firstName("a".repeat(61)))
        .isInstanceOf(ValidationException.class)
        .hasMessage("First Name must be at most 60 characters");
    assertThatThrownBy(() -> Validator.firstName("John123"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("First Name contains Invalid Characters, Please Enter a Valid FirstName");
  }

  @Test
  void lastNameValidation() {
    assertThat(Validator.lastName(null)).isNull();
    assertThat(Validator.lastName("")).isNull();
    assertThat(Validator.lastName("  ")).isNull();
    assertThat(Validator.lastName("Smith")).isEqualTo("Smith");
    assertThatThrownBy(() -> Validator.lastName("a".repeat(61)))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Last Name must be at most 60 characters");
    assertThatThrownBy(() -> Validator.lastName("Smith99"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Last Name contains Invalid Characters, Please Enter a Valid LastName");
  }

  @Test
  void emailValidation() {
    assertThat(Validator.email("test@example.com")).isEqualTo("test@example.com");
    assertThatThrownBy(() -> Validator.email(null))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Email Address isn't valid");
    assertThatThrownBy(() -> Validator.email("bad-email"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Email Address isn't valid");
    assertThatThrownBy(() -> Validator.email("  ")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.email("a".repeat(250) + "@b.com"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Email Address isn't valid");
  }

  @Test
  void phoneValidation() {
    assertThat(Validator.phone("2125551212")).isEqualTo("2125551212");
    assertThatThrownBy(() -> Validator.phone(null))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Phone Number is required");
    assertThatThrownBy(() -> Validator.phone("0123456"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Phone Number Cannot start with a 0");
    assertThatThrownBy(() -> Validator.phone("(0)123456"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Phone Number Cannot start with a 0");
    assertThatThrownBy(() -> Validator.phone("123"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Phone Number must contain 6 to 14 digits and cannot start with a 0");
    assertThatThrownBy(() -> Validator.phone("123456789012345"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Phone Number must contain 6 to 14 digits and cannot start with a 0");
    assertThatThrownBy(() -> Validator.phone("abcd12345"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Phone Number must contain 6 to 14 digits and cannot start with a 0");
  }

  @Test
  void countryCodeValidation() {
    assertThat(Validator.countryCode(null)).isNull();
    assertThat(Validator.countryCode("")).isNull();
    assertThat(Validator.countryCode("   ")).isNull();
    assertThat(Validator.countryCode("1")).isEqualTo("1");
    assertThat(Validator.countryCode("91")).isEqualTo("91");
    assertThat(Validator.countryCode("850")).isEqualTo("850");
    assertThatThrownBy(() -> Validator.countryCode("01"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Country Code Cannot start with a 0");
    assertThatThrownBy(() -> Validator.countryCode("0"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Country Code Cannot start with a 0");
    assertThatThrownBy(() -> Validator.countryCode("12345"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Country Code must contain 1 to 4 digits and cannot start with a 0");
    assertThatThrownBy(() -> Validator.countryCode("abc"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Country Code must contain 1 to 4 digits and cannot start with a 0");
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
    assertThat(Validator.roleName("custom role")).isEqualTo("custom role");
    assertThat(Validator.roleName("custom-role-1")).isEqualTo("custom-role-1");
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
  void optionalNoteAndUnknownFieldValidation() {
    assertThat(Validator.optionalNote(null)).isNull();
    assertThat(Validator.optionalNote("   ")).isNull();
    assertThat(Validator.optionalNote(" note ")).isEqualTo("note");
    assertThatThrownBy(() -> Validator.optionalNote("bad\u0000note"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.optionalNote("a".repeat(1001)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.unknownField("unexpected"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("unexpected is not allowed");
    assertThatThrownBy(() -> Validator.rejectMemberIdChange("M-123"))
        .isInstanceOf(ValidationException.class);
    Validator.rejectMemberIdChange(null);
  }

  @Test
  void normalizationAndScalarValidationBoundaries() {
    assertThat(Validator.name("  Ana   María  ", "firstName", true)).isEqualTo("Ana María");
    assertThat(Validator.name("  ", "lastName", false)).isNull();
    assertThatThrownBy(() -> Validator.name("a\0b", "firstName", true))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.name("a".repeat(61), "custom", true))
        .isInstanceOf(ValidationException.class)
        .hasMessage("custom must be at most 60 characters");
    assertThatThrownBy(() -> Validator.name("123", "custom", true))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.email("a".repeat(65) + "@example.com"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.email("a@")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.email("bad\0@example.com"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.phone("   ")).isInstanceOf(ValidationException.class);
    assertThat(Validator.phone(" 1 (212) 555-1212 ")).isEqualTo("12125551212");
    assertThatThrownBy(() -> Validator.phone(" 0123456 ")).isInstanceOf(ValidationException.class);
    assertThat(Validator.username("  USER@example.com  ")).isEqualTo("USER@example.com");
    assertThatThrownBy(() -> Validator.username("a".repeat(65) + "@example.com"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.username("   ")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.username("a".repeat(255)))
        .isInstanceOf(ValidationException.class);
    assertThat(Validator.roleName("  VIEWER  ")).isEqualTo("viewer");
    assertThat(Validator.roleDescription(" description ")).isEqualTo("description");
    assertThatThrownBy(() -> Validator.roleDescription("bad\0description"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roles(Arrays.asList("viewer", null)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.roles(List.of("invalid!")))
        .isInstanceOf(ValidationException.class);
    assertThat(Validator.roles(List.of("aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh", "ii", "jj")))
        .hasSize(10);
    assertThatThrownBy(() -> Validator.cursor("a".repeat(257)))
        .isInstanceOf(ValidationException.class);
    assertThat(Validator.idempotencyKey("x")).isEqualTo("x");
    assertThatThrownBy(() -> Validator.idempotencyKey("bad\0key"))
        .isInstanceOf(ValidationException.class);
    assertThat(Validator.searchQuery("a".repeat(100))).hasSize(100);
    assertThatThrownBy(() -> Validator.searchQuery("bad\0query"))
        .isInstanceOf(ValidationException.class);
    assertThat(Validator.pageLimit(100)).isEqualTo(100);
    assertThatThrownBy(() -> Validator.pageLimit(101)).isInstanceOf(ValidationException.class);
  }

  @Test
  void entityValidationRejectsInvalidEntities() {
    assertThatThrownBy(() -> Validator.member(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.role(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.userAccount(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.auditLog(null)).isInstanceOf(ValidationException.class);

    com.kitchensink.model.Member invalidVersion = MemberForValidation.valid();
    invalidVersion.setVersion(0);
    assertThatThrownBy(() -> Validator.member(invalidVersion))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validator.memberId("AB")).isInstanceOf(ValidationException.class);
    com.kitchensink.model.Member missingActor = MemberForValidation.valid();
    missingActor.setCreatedBy(null);
    assertThatThrownBy(() -> Validator.member(missingActor))
        .isInstanceOf(ValidationException.class);

    com.kitchensink.model.UserAccount user = new com.kitchensink.model.UserAccount();
    user.setUsername(" USER@example.com ");
    user.setUsernameLower("user@example.com");
    user.setRoles(List.of("viewer"));
    user.setCreatedAt(java.time.Instant.now());
    user.setUpdatedAt(java.time.Instant.now());
    assertThatThrownBy(() -> Validator.userAccount(user)).isInstanceOf(ValidationException.class);
    user.setUsername("user@example.com");
    user.setUsernameLower("wrong@example.com");
    assertThatThrownBy(() -> Validator.userAccount(user)).isInstanceOf(ValidationException.class);
    user.setUsernameLower("user@example.com");
    user.setRoles(List.of("viewer", "viewer"));
    assertThatThrownBy(() -> Validator.userAccount(user)).isInstanceOf(ValidationException.class);
    user.setRoles(List.of("viewer"));
    user.setUpdatedAt(null);
    assertThatThrownBy(() -> Validator.userAccount(user)).isInstanceOf(ValidationException.class);
    user.setUpdatedAt(java.time.Instant.now());
    user.setCreatedAt(null);
    assertThatThrownBy(() -> Validator.userAccount(user)).isInstanceOf(ValidationException.class);

    com.kitchensink.model.Role role = new com.kitchensink.model.Role();
    role.setName("viewer");
    role.setDescription("viewer");
    role.setPermissions(List.of("member:create"));
    role.setUpdatedAt(java.time.Instant.now());
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);
    role.setPermissions(List.of("unsupported"));
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);
    role.setPermissions(null);
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);
    role.setPermissions(List.of("member:view", "member:view"));
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);
    role.setPermissions(List.of());
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);
    role.setPermissions(List.of("member:update"));
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);
    role.setPermissions(List.of("member:view", "member:update"));
    Validator.role(role);
    role.setPermissions(List.of("member:view"));
    role.setUpdatedAt(null);
    assertThatThrownBy(() -> Validator.role(role)).isInstanceOf(ValidationException.class);

    com.kitchensink.model.AuditLog audit = new com.kitchensink.model.AuditLog();
    audit.setAt(java.time.Instant.now());
    audit.setActorId(new ObjectId());
    audit.setAction("member.create");
    audit.setEntityType("member");
    audit.setEntityId(new ObjectId());
    audit.setChanges(Map.of("safe", "value"));
    audit.setRequestId("request");
    audit.setIp("127.0.0.1");
    Validator.auditLog(audit);
    audit.setChanges(Map.of("password", "secret"));
    assertThatThrownBy(() -> Validator.auditLog(audit)).isInstanceOf(ValidationException.class);

    assertInvalidAudit(log -> log.setId(new ObjectId()));
    assertInvalidAudit(log -> log.setActorId(null));
    assertInvalidAudit(log -> log.setEntityId(null));
    assertInvalidAudit(log -> log.setAction("unknown"));
    assertInvalidAudit(log -> log.setAt(null));
    assertInvalidAudit(log -> log.setEntityType(" "));
    assertInvalidAudit(log -> log.setEntityType(null));
    assertInvalidAudit(log -> log.setEntityType("x".repeat(41)));
    assertInvalidAudit(log -> log.setRequestId(null));
    assertInvalidAudit(log -> log.setRequestId(" "));
    assertInvalidAudit(log -> log.setRequestId("x".repeat(129)));
    assertInvalidAudit(log -> log.setChanges(null));
    assertInvalidAudit(
        log -> {
          Map<String, Object> changes = new HashMap<>();
          changes.put("$set", "value");
          log.setChanges(changes);
        });
    assertInvalidAudit(
        log -> {
          Map<String, Object> changes = new HashMap<>();
          changes.put("nested.key", "value");
          log.setChanges(changes);
        });
    assertInvalidAudit(
        log -> {
          Map<String, Object> changes = new HashMap<>();
          changes.put(null, "value");
          log.setChanges(changes);
        });
    assertInvalidAudit(log -> log.setIp("x".repeat(65)));

    com.kitchensink.model.Member missingTimestamps = MemberForValidation.valid();
    missingTimestamps.setUpdatedAt(null);
    assertThatThrownBy(() -> Validator.member(missingTimestamps))
        .isInstanceOf(ValidationException.class);
    com.kitchensink.model.Member missingCreatedAt = MemberForValidation.valid();
    missingCreatedAt.setCreatedAt(null);
    assertThatThrownBy(() -> Validator.member(missingCreatedAt))
        .isInstanceOf(ValidationException.class);
    com.kitchensink.model.Member missingUpdatedBy = MemberForValidation.valid();
    missingUpdatedBy.setUpdatedBy(null);
    assertThatThrownBy(() -> Validator.member(missingUpdatedBy))
        .isInstanceOf(ValidationException.class);
    com.kitchensink.model.Member missingVersion = MemberForValidation.valid();
    missingVersion.setVersion(null);
    assertThatThrownBy(() -> Validator.member(missingVersion))
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

  private static final class MemberForValidation {
    private static com.kitchensink.model.Member valid() {
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
      return member;
    }
  }

  private static void assertInvalidAudit(Consumer<com.kitchensink.model.AuditLog> invalidation) {
    com.kitchensink.model.AuditLog audit = new com.kitchensink.model.AuditLog();
    audit.setAt(java.time.Instant.now());
    audit.setActorId(new ObjectId());
    audit.setAction("member.create");
    audit.setEntityType("member");
    audit.setEntityId(new ObjectId());
    audit.setChanges(Map.of("safe", "value"));
    audit.setRequestId("request");
    invalidation.accept(audit);
    assertThatThrownBy(() -> Validator.auditLog(audit)).isInstanceOf(ValidationException.class);
  }
}
