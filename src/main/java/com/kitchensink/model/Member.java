package com.kitchensink.model;

import com.kitchensink.validation.Validator;
import java.io.Serializable;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** Member directory record. */
@SuppressWarnings("serial")
@Document(collection = "members")
public class Member implements Serializable {
  @Id private ObjectId id;
  private String memberId;
  private String firstName;
  private String lastName;
  private String fullNameLower;
  private String email;
  private String emailLower;
  private String phone;
  private String status;
  private Instant createdAt;
  private ObjectId createdBy;
  private Instant updatedAt;
  private ObjectId updatedBy;
  private Integer version;
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
  }

  public String getEmailLower() {
    return emailLower;
  }

  public void setEmailLower(String emailLower) {
    this.emailLower = emailLower;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public ObjectId getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(ObjectId createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public ObjectId getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(ObjectId updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
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
