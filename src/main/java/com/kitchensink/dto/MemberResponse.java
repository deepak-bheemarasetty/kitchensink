package com.kitchensink.dto;

import com.kitchensink.model.Member;
import org.bson.types.ObjectId;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** Member response projection for API and UI consumers. */
public final class MemberResponse {
  private final ObjectId id;
  private final String memberId;
  private final String name;
  private final String firstName;
  private final String lastName;
  private final String email;
  private final String countryCode;
  private final String phone;
  private final String rawPhone;
  private final String status;
  private final Integer version;

  private MemberResponse(
      ObjectId id,
      String memberId,
      String name,
      String firstName,
      String lastName,
      String email,
      String countryCode,
      String phone,
      String rawPhone,
      String status,
      Integer version) {
    this.id = id;
    this.memberId = memberId;
    this.name = name;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.countryCode = countryCode;
    this.phone = phone;
    this.rawPhone = rawPhone;
    this.status = status;
    this.version = version;
  }

  /**
   * Constructs a MemberResponse from a persistent Member entity.
   *
   * @param member source entity
   * @return formatted response DTO
   */
  public static MemberResponse from(Member member) {
    if (member == null) {
      return null;
    }
    String firstName = member.getFirstName() == null ? "" : member.getFirstName();
    String lastName = member.getLastName() == null ? "" : member.getLastName();
    String fullName = (firstName + " " + lastName).trim();
    String countryCode = member.getCountryCode();
    String rawPhone = member.getPhone();
    String formattedPhone;
    if (rawPhone == null || rawPhone.isBlank()) {
      formattedPhone = "";
    } else if (countryCode == null || countryCode.isBlank()) {
      formattedPhone = rawPhone;
    } else {
      formattedPhone = "+" + countryCode + "-" + rawPhone;
    }

    return new MemberResponse(
        member.getId(),
        member.getMemberId(),
        fullName,
        member.getFirstName(),
        member.getLastName(),
        member.getEmail(),
        countryCode,
        formattedPhone,
        rawPhone,
        member.getStatus(),
        member.getVersion());
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public ObjectId getId() {
    return id;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getName() {
    return name;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public String getPhone() {
    return phone;
  }

  public String getRawPhone() {
    return rawPhone;
  }

  public String getStatus() {
    return status;
  }

  public Integer getVersion() {
    return version;
  }
}
