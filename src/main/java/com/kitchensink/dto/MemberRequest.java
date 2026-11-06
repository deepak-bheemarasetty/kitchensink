package com.kitchensink.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Member fields accepted from a create request. */
public final class MemberRequest {
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String country;

  private MemberRequest() {}

  @JsonCreator
  public static MemberRequest of(
      @JsonProperty("firstName") String firstName,
      @JsonProperty("lastName") String lastName,
      @JsonProperty("email") String email,
      @JsonProperty("phone") String phone,
      @JsonProperty("country") String country) {
    MemberRequest request = new MemberRequest();
    request.firstName = firstName;
    request.lastName = lastName;
    request.email = email;
    request.phone = phone;
    request.country = country;
    return request;
  }

  public static MemberRequest empty() { return new MemberRequest(); }
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }
}
