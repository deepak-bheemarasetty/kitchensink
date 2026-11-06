package com.kitchensink.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberRequestTest {
  @Test
  void preservesNullNameAndEmail() {
    MemberRequest request = MemberRequest.of(null, null, null, null);

    assertThat(request.getName()).isNull();
    assertThat(request.getEmail()).isNull();
  }

  @Test
  void constructorIsPrivate() throws Exception {
    assertThat(MemberRequest.class.getDeclaredConstructor().canAccess(null)).isFalse();
  }
}
