package com.kitchensink.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kitchensink.validation.Validator;

/** Member fields accepted from a create request. */
public final class MemberRequest {
  private String firstName;
  private String lastName;
  private String email;
  private String countryCode;
  private String phone;

  private MemberRequest() {}

  /**
   * Creates a member request from individual property values.
   *
   * @param firstName member first name
   * @param lastName member last name
   * @param email member email
   * @param phone member phone number
   * @return initialized member request
   */
  @JsonCreator
  public static MemberRequest of(
      @JsonProperty("firstName") String firstName,
      @JsonProperty("lastName") String lastName,
      @JsonProperty("email") String email,
      @JsonProperty("countryCode") String countryCode,
      @JsonProperty("phone") String phone) {
    MemberRequest request = new MemberRequest();
    request.firstName = firstName;
    request.lastName = lastName;
    request.email = email;
    request.countryCode = countryCode;
    request.phone = phone;
    return request;
  }

  public static MemberRequest empty() {
    return new MemberRequest();
  }

  /** Normalizes and validates request fields before they are mapped to a member. */
  public void validate() {
    firstName = Validator.firstName(firstName);
    lastName = Validator.lastName(lastName);
    email = Validator.email(email);
    countryCode = Validator.countryCode(countryCode);
    phone = Validator.phone(phone);
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  @JsonAnySetter
  public void rejectUnknown(String field, Object value) {
    Validator.unknownField(field);
  }
}
