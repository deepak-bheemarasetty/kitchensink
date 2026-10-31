package com.kitchensink.service;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/** Tracks most-viewed member summaries in memory for the home page. */
@Service
public class FeaturedMemberCacheService {
  private static final int FEATURED_MEMBER_LIMIT = 5;

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

  /** Returns up to five cached members without querying MongoDB. */
  public List<CachedMember> getFeaturedMembers() {
    return membersById.values().stream().sorted(MOST_VIEWED_ORDER).limit(FEATURED_MEMBER_LIMIT)
        .toList();
  }

  /** Loads a member from MongoDB and updates its in-memory view count. */
  public Optional<Member> recordView(String memberId) {
    return memberRepository
        .findById(memberId)
        .map(
            member -> {
              membersById.compute(
                  memberId,
                  (id, cached) ->
                      new CachedMember(
                          member.getId(),
                          member.getName(),
                          member.getEmail(),
                          cached == null ? 1 : cached.viewCount() + 1));
              return member;
            });
  }

  /** Cached member list entry, including the in-memory view count. */
  public record CachedMember(String id, String name, String email, long viewCount) {}
}
