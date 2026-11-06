package com.kitchensink.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kitchensink.validation.Validator;

/** Member fields accepted from a create request. */
public final class MemberRequest {
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
    firstName = Validator.firstName(firstName);
    lastName = Validator.lastName(lastName);
    email = Validator.email(email);
    phone = Validator.phone(phone);
  }

  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  @JsonAnySetter
  public void rejectUnknown(String field, Object value) {
    Validator.unknownField(field);
  }
}
