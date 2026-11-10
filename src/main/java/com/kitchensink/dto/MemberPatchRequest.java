package com.kitchensink.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kitchensink.validation.Validator;

/**
 * Editable member fields and the version observed by the client.
 *
 * @author Deepak Bheemarasetty
 */
public class MemberPatchRequest {
  private Integer version;
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String countryCode;
  private String memberId;
  private String status;

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
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

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(String memberId) {
    this.memberId = memberId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  /** Validates the patch request fields. */
  public void validate() {
    Validator.version(version);
    Validator.rejectMemberIdChange(memberId);
    if (firstName != null) {
      firstName = Validator.firstName(firstName);
    }
    if (lastName != null) {
      lastName = Validator.lastName(lastName);
    }
    if (email != null) {
      email = Validator.email(email);
    }
    if (phone != null) {
      phone = Validator.phone(phone);
    }
    if (countryCode != null) {
      countryCode = Validator.countryCode(countryCode);
    }
    if (status != null) {
      status = Validator.status(status);
    }
  }

  @JsonAnySetter
  public void rejectUnknown(String field, Object value) {
    Validator.unknownField(field);
  }
}
