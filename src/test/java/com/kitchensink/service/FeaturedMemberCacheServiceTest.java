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
    when(memberRepository.findById("missing")).thenReturn(Optional.empty());

    assertThat(cacheService.recordView("missing")).isEmpty();
    assertThat(cacheService.getFeaturedMembers()).isEmpty();

    verify(memberRepository).findById("missing");
  }

  @Test
  void recordViewAddsMemberAndIncrementsItsInMemoryCount() {
    Member member = member("member-1", "Jane Doe", "jane@example.com");
    when(memberRepository.findById("member-1")).thenReturn(Optional.of(member));

    assertThat(cacheService.recordView("member-1")).contains(member);
    assertThat(cacheService.getFeaturedMembers())
        .containsExactly(
            new FeaturedMemberCacheService.CachedMember(
                "member-1", "Jane Doe", "jane@example.com", 1));

    cacheService.recordView("member-1");

    assertThat(cacheService.getFeaturedMembers().getFirst().viewCount()).isEqualTo(2);
    verify(memberRepository, org.mockito.Mockito.times(2)).findById("member-1");
  }

  @Test
  void featuredMembersAreOrderedByViewsThenNameThenIdAndLimitedToFive() {
    List<Member> members =
        List.of(
            member("id-b", "Same Name", "b@example.com"),
            member("id-a", "Same Name", "a@example.com"),
            member("id-z", "Zed", "zed@example.com"),
            member("id-alpha", "Alpha", "alpha@example.com"),
            member("id-beta", "Beta", "beta@example.com"),
            member("id-gamma", "Gamma", "gamma@example.com"));
    members.forEach(member -> when(memberRepository.findById(member.getId()))
        .thenReturn(Optional.of(member)));

    members.forEach(member -> cacheService.recordView(member.getId()));
    cacheService.recordView("id-gamma");

    assertThat(cacheService.getFeaturedMembers())
        .extracting(FeaturedMemberCacheService.CachedMember::id)
        .containsExactly("id-gamma", "id-alpha", "id-beta", "id-a", "id-b");
  }

  private static Member member(String id, String name, String email) {
    Member member = new Member();
    member.setId(id);
    member.setName(name);
    member.setEmail(email);
    member.setPhoneNumber("2125551234");
    return member;
  }
}
