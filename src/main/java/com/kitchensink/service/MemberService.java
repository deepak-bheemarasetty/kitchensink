package com.kitchensink.service;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.time.Instant;
import java.util.Locale;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Applies member normalization and persists directory records. */
@Service
public class MemberService {
  private final MemberRepository memberRepository;

  public MemberService(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  public Member register(Member member) {
    member.setFirstName(member.getFirstName().trim());
    member.setLastName(member.getLastName().trim());
    member.setEmail(member.getEmail().trim());
    member.setEmailLower(member.getEmail().toLowerCase(Locale.ROOT));
    member.setFullNameLower((member.getFirstName() + " " + member.getLastName()).toLowerCase(Locale.ROOT));
    member.setMemberId(Instant.now().toEpochMilli() + "-" + new ObjectId().toHexString().substring(0, 6).toUpperCase(Locale.ROOT));
    member.setStatus("active");
    member.setCreatedAt(Instant.now());
    member.setUpdatedAt(member.getCreatedAt());
    member.setVersion(1);
    member.setCreatedBy(new ObjectId());
    member.setUpdatedBy(member.getCreatedBy());
    try {
      return memberRepository.save(member);
    } catch (DuplicateKeyException exception) {
      throw new DuplicateEmailException("Email already registered: " + member.getEmail());
    }
  }

  /** Finds a member by email, reporting inactive records distinctly from missing records. */
  public Member findByEmail(String email) {
    Member member =
        memberRepository
            .findByEmail(email.trim())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    if (!"active".equalsIgnoreCase(member.getStatus()) || member.getDeletedAt() != null) {
      throw new ResponseStatusException(HttpStatus.GONE, "Member is no longer active");
    }
    return member;
  }
}
