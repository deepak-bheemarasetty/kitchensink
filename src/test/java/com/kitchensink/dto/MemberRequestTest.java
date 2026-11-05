package com.kitchensink.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberRequestTest {
  @Test
  void preservesNullNameAndEmail() {
    MemberRequest request = new MemberRequest(null, null, null);

    assertThat(request.name()).isNull();
    assertThat(request.email()).isNull();
  }
}
