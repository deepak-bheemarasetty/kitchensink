package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
  @Mock private MemberRepository memberRepository;

  @Mock private SequenceGeneratorService sequenceGeneratorService;

  @InjectMocks private MemberService memberService;

  private Member member;

  @BeforeEach
  void setUp() {
    member = member(1L);
  }

  @Test
  void registerSavesMemberWithNextSequenceValue() {
    when(sequenceGeneratorService.nextMemberId()).thenReturn(1L);
    when(memberRepository.save(member)).thenReturn(member);

    assertThat(memberService.register(member)).isSameAs(member);

    assertThat(member.getId()).isEqualTo(1L);
    verify(memberRepository).save(member);
    verify(memberRepository, never()).findByEmailIgnoreCase(any());
  }

  @Test
  void translatesDatabaseUniqueIndexConflictToDuplicateEmailException() {
    when(sequenceGeneratorService.nextMemberId()).thenReturn(1L);
    when(memberRepository.save(member)).thenThrow(new DuplicateKeyException("email index"));

    assertThatThrownBy(() -> memberService.register(member))
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessageContaining(member.getEmail());

    verify(memberRepository).save(member);
  }

  @Test
  void normalizesNameAndEmailBeforeSaving() {
    member.setName(" Jane Doe ");
    member.setEmail(" Jane@Example.COM ");
    when(sequenceGeneratorService.nextMemberId()).thenReturn(1L);
    when(memberRepository.save(member)).thenReturn(member);

    memberService.register(member);

    assertThat(member.getName()).isEqualTo("Jane Doe");
    assertThat(member.getEmail()).isEqualTo("jane@example.com");
  }

  @Test
  void passesNullFieldsThroughForMongoSchemaEnforcement() {
    member.setName(null);
    member.setEmail(null);
    when(sequenceGeneratorService.nextMemberId()).thenReturn(1L);
    when(memberRepository.save(member)).thenReturn(member);

    assertThat(memberService.register(member)).isSameAs(member);

    assertThat(member.getName()).isNull();
    assertThat(member.getEmail()).isNull();
  }

  @Test
  void concurrentDuplicateInsertIsResolvedByMongoUniqueIndex() throws Exception {
    AtomicBoolean firstInsert = new AtomicBoolean(true);
    when(sequenceGeneratorService.nextMemberId()).thenReturn(1L, 2L);
    when(memberRepository.save(any(Member.class)))
        .thenAnswer(
            invocation -> {
              if (firstInsert.compareAndSet(true, false)) {
                return invocation.getArgument(0);
              }
              throw new DuplicateKeyException("email index");
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Member> first = executor.submit(() -> memberService.register(member(1L)));
      Future<Member> second = executor.submit(() -> memberService.register(member(2L)));
      int saved = 0;
      int duplicate = 0;
      for (Future<Member> registration : java.util.List.of(first, second)) {
        try {
          assertThat(registration.get().getId()).isIn(1L, 2L);
          saved++;
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(DuplicateEmailException.class);
          duplicate++;
        }
      }
      assertThat(saved).isEqualTo(1);
      assertThat(duplicate).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private static Member member(Long id) {
    Member member = Member.create();
    member.setId(id);
    member.setName("Jane Doe");
    member.setEmail("jane@example.com");
    member.setPhoneNumber("2125551234");
    member.setCountryCode(1);
    return member;
  }
}
