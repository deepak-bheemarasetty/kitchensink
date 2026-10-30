package com.kitchensink.service;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {
    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member register(Member member) {
        log.info("Registering {}", member.getName());
        if (memberRepository.findByEmail(member.getEmail()).isPresent()) {
            throw new DuplicateEmailException("Email already registered: " + member.getEmail());
        }
        return memberRepository.save(member);
    }
}
