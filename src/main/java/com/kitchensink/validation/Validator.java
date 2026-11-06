package com.kitchensink.validation;

import com.kitchensink.model.AuditLog;
import com.kitchensink.model.Member;
import com.kitchensink.model.Role;
import com.kitchensink.model.UserAccount;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Single source of application field and persistence-model validation rules. */
public final class Validator {
  private static final Pattern NAME = Pattern.compile("(?U)^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
  private static final Pattern EMAIL = Pattern.compile(
      "^[A-Za-z0-9_+\\-]+(?:\\.[A-Za-z0-9_+\\-]+)*@(?=.{1,255}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$");
  private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
  private static final Pattern ROLE = Pattern.compile("^[a-z0-9-]{2,30}$");
  private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$");
  private static final Set<String> PERMISSIONS = Set.of(
      "member:view", "member:create", "member:update", "member:delete", "user:manage", "role:manage");
  private static final Set<String> AUDIT_ACTIONS = Set.of(
      "member.create", "member.update", "member.delete", "member.restore",
      "user.roleChange", "role.update", "auth.loginFailed");

  private Validator() {}

  public static String firstName(String value) { return name(value, "firstName", true); }
  public static String lastName(String value) { return name(value, "lastName", false); }

  public static String name(String value, String field, boolean required) {
    if (value == null) {
      if (required) fail(field, "is required");
      return null;
    }
    String normalized = Normalizer.normalize(value.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    rejectControls(normalized, field);
    if (normalized.isEmpty()) {
      if (required) fail(field, "is required");
      return null;
    }
    if (normalized.length() > 50 || !NAME.matcher(normalized).matches()
        || normalized.codePoints().noneMatch(Character::isLetter)) fail(field, "is invalid");
    return normalized;
  }

  public static String email(String value) {
    String normalized = normalizeRequired(value, "email", 254);
    if (normalized.length() < 3 || !EMAIL.matcher(normalized).matches()) fail("email", "format is invalid");
    if (normalized.substring(0, normalized.indexOf('@')).length() > 64) fail("email", "local part exceeds 64 characters");
    return normalized;
  }

  public static String phone(String value) {
    String normalized = normalizeRequired(value, "phone", 32).replaceAll("[\\s()-]", "");
    if (!PHONE.matcher(normalized).matches()) fail("phone", "must use E.164 format");
    return normalized;
  }

  public static String optionalNote(String value) {
    if (value == null) return null;
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    rejectControls(normalized, "notes");
    if (normalized.length() > 1000) fail("notes", "must be at most 1000 characters");
    return normalized.isEmpty() ? null : normalized;
  }

  public static String memberId(String value) {
    if (value == null || value.length() < 3 || value.length() > 20 || !value.matches("[A-Za-z0-9-]+")) fail("memberId", "must contain 3 to 20 letters, digits, or hyphens");
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public static String status(String value) {
    if (!"active".equals(value) && !"inactive".equals(value)) fail("status", "must be active or inactive");
    return value;
  }

  public static String username(String value) {
    String normalized = normalizeRequired(value, "username", 254);
    if (!EMAIL.matcher(normalized).matches()
        || normalized.substring(0, normalized.indexOf('@')).length() > 64) {
      fail("username", "must be a valid email address");
    }
    return normalized;
  }

  public static String roleName(String value) {
    if (value == null) fail("name", "is required");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!ROLE.matcher(normalized).matches()) fail("name", "must be 2 to 30 lowercase letters, digits, or hyphens");
    return normalized;
  }

  public static void unknownField(String field) {
    fail(field, "is not allowed");
  }

  public static String roleDescription(String value) {
    if (value == null) fail("description", "is required");
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    rejectControls(normalized, "description");
    if (normalized.length() > 200) fail("description", "must be at most 200 characters");
    return normalized;
  }

  public static java.util.List<String> roles(List<String> values) {
    if (values == null || values.isEmpty() || values.size() > 10
        || new HashSet<>(values).size() != values.size()) {
      fail("roles", "must contain 1 to 10 unique role names");
    }
    for (String value : values) {
      if (value == null || !ROLE.matcher(value).matches()) fail("roles", "contains an invalid role name");
    }
    return List.copyOf(values);
  }

  public static String newPassword(String value, String username) {
    if (value == null || value.length() < 12 || value.length() > 128) fail("password", "must be 12 to 128 characters");
    if (username != null && value.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) fail("password", "cannot contain the username");
    return value;
  }

  public static int version(Integer value) {
    if (value == null || value < 1) fail("version", "is required and must be positive");
    return value;
  }

  public static String sortField(String value) {
    if (!"name".equals(value) && !"email".equals(value)) fail("sort", "must be name or email");
    return value;
  }

  public static String searchQuery(String value) {
    if (value != null && value.length() > 100) fail("q", "must be at most 100 characters");
    if (value != null) rejectControls(value, "q");
    return value;
  }

  public static int pageLimit(int value) {
    if (value < 1 || value > 100) fail("limit", "must be between 1 and 100");
    return value;
  }

  public static String cursor(String value) {
    if (value != null && value.length() > 256) fail("cursor", "is too long");
    return value;
  }

  public static String idempotencyKey(String value) {
    if (value == null || value.isBlank() || value.length() > 128) fail("Idempotency-Key", "must contain 1 to 128 characters");
    rejectControls(value, "Idempotency-Key");
    return value;
  }

  public static void rejectMemberIdChange(String memberId) {
    if (memberId != null) fail("memberId", "cannot be changed");
  }

  public static void userAccount(UserAccount user) {
    if (user == null) fail("user", "is required");
    if (!username(user.getUsername()).equals(user.getUsername())) fail("username", "must be normalized");
    if (!user.getUsername().toLowerCase(Locale.ROOT).equals(user.getUsernameLower())) fail("usernameLower", "must match normalized username");
    String hash = user.getPasswordHash();
    if (hash == null || (!BCRYPT.matcher(hash).matches() && !hash.startsWith("$argon2id$"))) fail("passwordHash", "must contain a BCrypt or Argon2id hash");
    roles(user.getRoles());
    if (user.getCreatedAt() == null || user.getUpdatedAt() == null) fail("timestamps", "createdAt and updatedAt are required");
  }

  public static void role(Role role) {
    if (role == null) fail("role", "is required");
    roleName(role.getName());
    roleDescription(role.getDescription());
    if (role.getPermissions() == null || role.getPermissions().isEmpty()
        || new HashSet<>(role.getPermissions()).size() != role.getPermissions().size()
        || !PERMISSIONS.containsAll(role.getPermissions())) fail("permissions", "must contain unique supported permissions");
    if (role.getPermissions().stream().anyMatch(permission -> Set.of("member:create", "member:update", "member:delete").contains(permission))
        && !role.getPermissions().contains("member:view")) fail("permissions", "member write permissions require member:view");
    if (role.getUpdatedAt() == null) fail("updatedAt", "is required");
  }

  public static void auditLog(AuditLog audit) {
    if (audit == null) fail("audit", "is required");
    if (audit.getId() != null) fail("_id", "must be generated by MongoDB");
    if (audit.getAt() == null || audit.getActorId() == null || audit.getEntityId() == null) fail("audit", "timestamp, actor, and entity are required");
    if (!AUDIT_ACTIONS.contains(audit.getAction())) fail("action", "is not supported");
    if (audit.getEntityType() == null || audit.getEntityType().isBlank() || audit.getEntityType().length() > 40) fail("entityType", "is invalid");
    if (audit.getRequestId() == null || audit.getRequestId().isBlank() || audit.getRequestId().length() > 128) fail("requestId", "is invalid");
    if (audit.getChanges() == null) fail("changes", "is required");
    if (audit.getChanges().keySet().stream().anyMatch(key -> key == null || key.toLowerCase(Locale.ROOT).contains("password") || key.startsWith("$") || key.contains("."))) fail("changes", "cannot contain secrets, null names, or MongoDB operator keys");
    if (audit.getIp() != null && audit.getIp().length() > 64) fail("ip", "is too long");
  }

  public static void member(Member member) {
    if (member == null) fail("member", "is required");
    firstName(member.getFirstName());
    lastName(member.getLastName());
    email(member.getEmail());
    phone(member.getPhone());
    memberId(member.getMemberId());
    status(member.getStatus());
    if (member.getVersion() == null || member.getVersion() < 1) fail("version", "must be positive");
    if (member.getCreatedAt() == null || member.getUpdatedAt() == null || member.getCreatedBy() == null || member.getUpdatedBy() == null) fail("audit", "member timestamps and actors are required");
  }

  private static String normalizeRequired(String value, String field, int maxLength) {
    if (value == null) fail(field, "is required");
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    rejectControls(normalized, field);
    if (normalized.isEmpty()) fail(field, "is required");
    if (normalized.length() > maxLength) fail(field, "is too long");
    return normalized;
  }

  private static void rejectControls(String value, String field) {
    if (value.codePoints().anyMatch(Character::isISOControl)) fail(field, "contains control characters");
  }

  private static void fail(String field, String reason) {
    throw new ValidationException(field, field + " " + reason);
  }
}
