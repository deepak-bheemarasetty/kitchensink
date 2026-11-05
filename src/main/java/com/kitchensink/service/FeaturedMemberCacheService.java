package com.kitchensink.service;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/** Tracks bounded in-memory member view counts for the home page. */
@Service
public class FeaturedMemberCacheService {
  private static final int FEATURED_MEMBER_LIMIT = 10;
  private static final int MAX_TRACKED_MEMBERS = 1_000;

  private static final Comparator<CachedMember> MOST_VIEWED_ORDER =
      Comparator.comparingLong(CachedMember::viewCount)
          .reversed()
          .thenComparing(CachedMember::name)
          .thenComparing(CachedMember::id);

  private final ConcurrentMap<String, CachedMember> membersById = new ConcurrentHashMap<>();
  private final MemberRepository memberRepository;

  public FeaturedMemberCacheService(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  /** Returns up to ten cached members without querying MongoDB. */
  public List<CachedMember> getFeaturedMembers() {
    return membersById.values().stream().sorted(MOST_VIEWED_ORDER).limit(FEATURED_MEMBER_LIMIT)
        .toList();
  }

  /** Returns a cached member or loads it from MongoDB, then updates its in-memory view count. */
  public Optional<Member> recordView(String memberId) {
    CachedMember cached =
        membersById.computeIfPresent(memberId, (id, member) -> member.withNextView());
    if (cached != null) {
      return Optional.of(cached.toMember());
    }
    return memberRepository.findById(memberId).map(this::recordView);
  }

  /** Adds a member already loaded by the caller to the cache and records its view. */
  public Member recordView(Member member) {
    membersById.compute(
        member.getId(),
        (id, cached) ->
            new CachedMember(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber(),
                cached == null ? 1 : cached.viewCount() + 1));
    trimCache();
    return member;
  }

  private synchronized void trimCache() {
    while (membersById.size() > MAX_TRACKED_MEMBERS) {
      Map.Entry<String, CachedMember> leastViewed =
          membersById.entrySet().stream()
              .max(Map.Entry.comparingByValue(MOST_VIEWED_ORDER))
              .orElseThrow();
      membersById.remove(leastViewed.getKey(), leastViewed.getValue());
    }
  }

  /** Cached member details, including the in-memory view count. */
  public record CachedMember(
      String id, String name, String email, String phoneNumber, long viewCount) {
    private CachedMember withNextView() {
      return new CachedMember(id, name, email, phoneNumber, viewCount + 1);
    }

    private Member toMember() {
      Member member = new Member();
      member.setId(id);
      member.setName(name);
      member.setEmail(email);
      member.setPhoneNumber(phoneNumber);
      return member;
    }
  }
}
