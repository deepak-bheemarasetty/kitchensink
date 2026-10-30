package com.kitchensink.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRequest(
        @NotNull
        @Size(min = 1, max = 25)
        @Pattern(regexp = "[\\p{L} ]+", message = "{name_validation_message}")
        String name,

        @NotNull
        @NotEmpty
        @Email
        String email,

        @NotNull
        @Size(min = 8, max = 12)
        @Digits(fraction = 0, integer = 12)
        String phoneNumber) {
}
