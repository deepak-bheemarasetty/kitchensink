package com.kitchensink.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.model.Member;
import com.kitchensink.validation.ValidationException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DTO classes.
 *
 * @author Deepak Bheemarasetty
 */
class DtoTest {

  @Test
  void memberRequestValidationAndAccessors() {
    MemberRequest request =
        MemberRequest.of("Jane", "Doe", "jane.doe@example.com", "1", "2125551212");
    request.setFirstName("Jane");
    request.setLastName("Doe");
    request.setEmail("jane.doe@example.com");
    request.setCountryCode("1");
    request.setPhone("2125551212");

    assertThat(request.getFirstName()).isEqualTo("Jane");
    assertThat(request.getLastName()).isEqualTo("Doe");
    assertThat(request.getEmail()).isEqualTo("jane.doe@example.com");
    assertThat(request.getCountryCode()).isEqualTo("1");
    assertThat(request.getPhone()).isEqualTo("2125551212");

    request.validate();

    // Unknown properties rejection
    assertThatThrownBy(() -> request.rejectUnknown("unknownProp", "value"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void memberPatchRequestValidationAndAccessors() {
    MemberPatchRequest patch = new MemberPatchRequest();
    patch.setFirstName("Alice");
    patch.setLastName("Smith");
    patch.setEmail("alice@example.com");
    patch.setCountryCode("44");
    patch.setPhone("2079460192");
    patch.setStatus("inactive");
    patch.setVersion(2);

    assertThat(patch.getFirstName()).isEqualTo("Alice");
    assertThat(patch.getLastName()).isEqualTo("Smith");
    assertThat(patch.getEmail()).isEqualTo("alice@example.com");
    assertThat(patch.getCountryCode()).isEqualTo("44");
    assertThat(patch.getPhone()).isEqualTo("2079460192");
    assertThat(patch.getStatus()).isEqualTo("inactive");
    assertThat(patch.getVersion()).isEqualTo(2);

    patch.validate();

    assertThatThrownBy(() -> patch.rejectUnknown("badKey", 123))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void memberResponseFormatting() {
    Member member = Member.create();
    member.setId(new ObjectId());
    member.setMemberId("M-999");
    member.setFirstName("Bob");
    member.setLastName("Jones");
    member.setEmail("bob@example.com");
    member.setCountryCode("91");
    member.setPhone("9876543210");
    member.setStatus("active");
    member.setVersion(1);

    MemberResponse response = MemberResponse.from(member);
    assertThat(response.getId()).isEqualTo(member.getId());
    assertThat(response.getMemberId()).isEqualTo("M-999");
    assertThat(response.getName()).isEqualTo("Bob Jones");
    assertThat(response.getFirstName()).isEqualTo("Bob");
    assertThat(response.getLastName()).isEqualTo("Jones");
    assertThat(response.getEmail()).isEqualTo("bob@example.com");
    assertThat(response.getCountryCode()).isEqualTo("91");
    assertThat(response.getPhone()).isEqualTo("+91-9876543210");
    assertThat(response.getRawPhone()).isEqualTo("9876543210");
    assertThat(response.getStatus()).isEqualTo("active");
    assertThat(response.getVersion()).isEqualTo(1);

    // Member with no country code
    member.setCountryCode(null);
    MemberResponse respNoCc = MemberResponse.from(member);
    assertThat(respNoCc.getPhone()).isEqualTo("9876543210");

    // Member with no last name
    member.setLastName(null);
    MemberResponse respNoLast = MemberResponse.from(member);
    assertThat(respNoLast.getName()).isEqualTo("Bob");
  }
}
