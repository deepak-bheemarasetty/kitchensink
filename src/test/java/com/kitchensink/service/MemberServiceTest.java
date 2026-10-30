package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests member registration business rules.
 *
 * @author Deepak Bheemarasetty
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
  @Mock private MemberRepository memberRepository;

  @InjectMocks private MemberService memberService;

  private Member member;

  @BeforeEach
  void setUp() {
    member = new Member();
    member.setName("Jane Doe");
    member.setEmail("jane@example.com");
    member.setPhoneNumber("2125551234");
  }

  @Test
  void registerSavesMemberWhenEmailIsNew() {
    when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.empty());
    when(memberRepository.save(member)).thenReturn(member);

    assertThat(memberService.register(member)).isSameAs(member);

    verify(memberRepository).save(member);
  }

  @Test
  void registerRejectsExistingEmailWithoutSaving() {
    when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> memberService.register(member))
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessageContaining(member.getEmail());

    verify(memberRepository, never()).save(member);
  }
}
