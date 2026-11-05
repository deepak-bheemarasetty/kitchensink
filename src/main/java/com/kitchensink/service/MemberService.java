package com.kitchensink.service;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Applies business rules for member registration.
 *
 * @author Deepak Bheemarasetty
 */
@Service
public class MemberService {
  private final MemberRepository memberRepository;

  public MemberService(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  /** Registers a member after ensuring its email address is unique. */
  public Member register(Member member) {
    member.setName(member.getName().trim());
    member.setEmail(member.getEmail().trim().toLowerCase(Locale.ROOT));
    if (memberRepository.findByEmailIgnoreCase(member.getEmail()).isPresent()) {
      throw new DuplicateEmailException("Email already registered: " + member.getEmail());
    }
    try {
      return memberRepository.save(member);
    } catch (DuplicateKeyException exception) {
      throw new DuplicateEmailException("Email already registered: " + member.getEmail());
    }
  }
}
