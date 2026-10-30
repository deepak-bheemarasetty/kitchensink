package com.kitchensink.repository;

import java.util.List;
import java.util.Optional;

import com.kitchensink.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    List<Member> findAllByOrderByNameAsc();
}
