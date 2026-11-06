package com.kitchensink.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Member fields accepted from a create request. */
public final class MemberRequest {
  private static final Pattern NAME = Pattern.compile("(?U)^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9_+\\-]+(?:\\.[A-Za-z0-9_+\\-]+)*@(?=.{1,255}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$");
  private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
  private String firstName;
  private String lastName;
  private String email;
  private String phone;

  private MemberRequest() {}

  @JsonCreator
  public static MemberRequest of(
      @JsonProperty("firstName") String firstName,
      @JsonProperty("lastName") String lastName,
      @JsonProperty("email") String email,
      @JsonProperty("phone") String phone) {
    MemberRequest request = new MemberRequest();
    request.firstName = firstName;
    request.lastName = lastName;
    request.email = email;
    request.phone = phone;
    return request;
  }

  public static MemberRequest empty() { return new MemberRequest(); }

  /** Normalizes and validates request fields before they are mapped to a member. */
  public void validate() {
    firstName = normalize(firstName, "firstName", true);
    lastName = normalize(lastName, "lastName", false);
    email = normalize(email, "email", true);
    phone = normalize(phone, "phone", true);
    if (!NAME.matcher(firstName).matches() || !firstName.codePoints().anyMatch(Character::isLetter)) {
      throw new IllegalArgumentException("First name is invalid");
    }
    if (lastName != null && !lastName.isEmpty()
        && (!NAME.matcher(lastName).matches() || !lastName.codePoints().anyMatch(Character::isLetter))) {
      throw new IllegalArgumentException("Last name is invalid");
    }
    validateEmail(email);
    if (!PHONE.matcher(phone.replaceAll("[\\s()-]", "")).matches()) {
      throw new IllegalArgumentException("Phone must be in E.164 format");
    }
  }

  private static String normalize(String value, String field, boolean required) {
    if (value == null) {
      if (required) throw new IllegalArgumentException(field + " is required");
      return null;
    }
    String normalized = Normalizer.normalize(value.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    if (normalized.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(field + " contains a control character");
    }
    if (normalized.isEmpty()) {
      if (required) throw new IllegalArgumentException(field + " is required");
      return null;
    }
    int max = field.equals("email") ? 254 : field.equals("phone") ? 16 : 50;
    if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
    return normalized;
  }

  private static void validateEmail(String value) {
    if (!EMAIL.matcher(value).matches() || value.length() < 3) {
      throw new IllegalArgumentException("Email format is invalid");
    }
    String local = value.substring(0, value.indexOf('@'));
    if (local.length() > 64) throw new IllegalArgumentException("Email local part is too long");
  }

  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
}
