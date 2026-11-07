package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests the process-local cache of most-viewed member summaries. */
@ExtendWith(MockitoExtension.class)
class FeaturedMemberCacheServiceTest {
  @Mock private MemberRepository memberRepository;

  private FeaturedMemberCacheService cacheService;

  @BeforeEach
  void setUp() {
    cacheService = new FeaturedMemberCacheService(memberRepository);
  }

  @Test
  void cacheStartsEmptyWithoutQueryingMongoDb() {
    assertThat(cacheService.getFeaturedMembers()).isEmpty();

    verifyNoInteractions(memberRepository);
  }

  @Test
  void missingMemberDoesNotEnterCache() {
    when(memberRepository.findById(9999L)).thenReturn(Optional.empty());

    assertThat(cacheService.recordView(9999L)).isEmpty();
    assertThat(cacheService.getFeaturedMembers()).isEmpty();

    verify(memberRepository).findById(9999L);
  }

  @Test
  void recordViewAddsMemberAndIncrementsItsInMemoryCount() {
    Member member = member(1L, "Jane Doe", "jane@example.com");
    when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

    assertThat(cacheService.recordView(1L)).contains(member);
    assertThat(cacheService.getFeaturedMembers())
        .containsExactly(
            new FeaturedMemberCacheService.CachedMember(
                1L, "Jane Doe", "jane@example.com", "2125551234", 1, 1));

    cacheService.recordView(1L);

    assertThat(cacheService.getFeaturedMembers().getFirst().viewCount()).isEqualTo(2);
    verify(memberRepository).findById(1L);
  }

  @Test
  void selectingCachedMemberReturnsDetailsWithoutQueryingMongoDb() {
    Member member = member(1L, "Jane Doe", "jane@example.com");
    cacheService.recordView(member);

    assertThat(cacheService.recordView(1L))
        .get()
        .extracting(Member::getPhoneNumber)
        .isEqualTo("2125551234");
    assertThat(cacheService.getFeaturedMembers().getFirst().viewCount()).isEqualTo(2);
    verifyNoInteractions(memberRepository);
  }

  @Test
  void recordingSameMemberObjectIncrementsCachedCount() {
    Member member = member(1L, "Jane Doe", "jane@example.com");

    cacheService.recordView(member);
    cacheService.recordView(member);

    assertThat(cacheService.getFeaturedMembers().getFirst().viewCount()).isEqualTo(2);
  }

  @Test
  void evictsLeastViewedMemberWhenCacheLimitIsExceeded() {
    when(memberRepository.findById(1000L)).thenReturn(Optional.empty());
    for (int i = 0; i <= 1_000; i++) {
      cacheService.recordView(
          member((long) i, "Member %04d".formatted(i), "member%d@example.com".formatted(i)));
    }

    assertThat(cacheService.recordView(1000L)).isEmpty();
    verify(memberRepository).findById(1000L);
  }

  @Test
  void featuredMembersAreOrderedByViewsThenNameThenIdAndLimitedToTen() {
    List<Member> members =
        List.of(
            member(2L, "Same Name", "b@example.com"),
            member(1L, "Same Name", "a@example.com"),
            member(6L, "Zed", "zed@example.com"),
            member(3L, "Alpha", "alpha@example.com"),
            member(4L, "Beta", "beta@example.com"),
            member(5L, "Gamma", "gamma@example.com"));
    members.forEach(
        member -> when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member)));

    members.forEach(member -> cacheService.recordView(member.getId()));
    cacheService.recordView(5L);

    assertThat(cacheService.getFeaturedMembers())
        .extracting(FeaturedMemberCacheService.CachedMember::id)
        .containsExactly(5L, 3L, 4L, 1L, 2L, 6L);
  }

  private static Member member(Long id, String name, String email) {
    Member member = Member.create();
    member.setId(id);
    member.setName(name);
    member.setEmail(email);
    member.setPhoneNumber("2125551234");
    member.setCountryCode(1);
    return member;
  }
}
