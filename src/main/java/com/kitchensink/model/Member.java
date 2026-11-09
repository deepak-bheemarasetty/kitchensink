package com.kitchensink.model;

import com.kitchensink.validation.Validator;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Member directory record.
 *
 * @author Deepak Bheemarasetty
 */
@SuppressWarnings("serial")
@Document(collection = "members")
public class Member extends BaseEntity {
  @Id private ObjectId id;
  private String memberId;
  private String firstName;
  private String lastName;
  private String fullNameLower;
  private String email;
  private String emailLower;
  private String countryCode;
  private String phone;
  private String status;
  private Instant deletedAt;
  private ObjectId deletedBy;

  private Member() {}

  public static Member create() {
    return new Member();
  }

  public String getFullName() {
    return (firstName + " " + (lastName == null ? "" : lastName)).trim();
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public ObjectId getId() {
    return id;
  }

  public void setId(ObjectId id) {
    this.id = id;
  }

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(String memberId) {
    this.memberId = memberId;
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

  public String getFullNameLower() {
    return fullNameLower;
  }

  public void setFullNameLower(String fullNameLower) {
    this.fullNameLower = fullNameLower;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
    this.emailLower = email == null ? null : email.toLowerCase();
  }

  public String getEmailLower() {
    return emailLower;
  }

  public void setEmailLower(String emailLower) {
    this.emailLower = emailLower;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public ObjectId getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(ObjectId deletedBy) {
    this.deletedBy = deletedBy;
  }

  public void validate() {
    Validator.member(this);
  }
}
