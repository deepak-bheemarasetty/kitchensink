package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

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

  @Test
  void concurrentRegistrationRaceIsResolvedByMongoUniqueEmailIndex() throws Exception {
    CountDownLatch registrationChecksComplete = new CountDownLatch(2);
    AtomicBoolean firstInsert = new AtomicBoolean(true);
    when(memberRepository.findByEmail(member.getEmail()))
        .thenAnswer(
            invocation -> {
              registrationChecksComplete.countDown();
              if (!registrationChecksComplete.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                    "Concurrent registration did not reach email check");
              }
              return Optional.empty();
            });
    when(memberRepository.save(member))
        .thenAnswer(
            invocation -> {
              if (firstInsert.compareAndSet(true, false)) {
                return member;
              }
              throw new DuplicateKeyException("Unique email index conflict");
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Member>> registrations =
          List.of(
              executor.submit(() -> memberService.register(member)),
              executor.submit(() -> memberService.register(member)));
      int saved = 0;
      int duplicate = 0;
      for (Future<Member> registration : registrations) {
        try {
          assertThat(registration.get()).isSameAs(member);
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
}
