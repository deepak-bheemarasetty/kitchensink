package com.kitchensink.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Validated input data used to register a member.
 *
 * @author Deepak Bheemarasetty
 */
public record MemberRequest(
    @NotBlank
        @Size(min = 1, max = 25)
        @Pattern(regexp = "(?=.*\\p{L})[\\p{L} ]+", message = "{name_validation_message}")
        String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 10, max = 12) @Digits(fraction = 0, integer = 12) String phoneNumber) {
  public MemberRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
