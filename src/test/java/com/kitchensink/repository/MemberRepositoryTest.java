package com.kitchensink.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.model.Member;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Tests member persistence behavior with an isolated database.
 *
 * @author Deepak Bheemarasetty
 */
@DataJpaTest
class MemberRepositoryTest {
  @Autowired private MemberRepository memberRepository;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
  }

  @Test
  void findByEmailReturnsMatchingMember() {
    Member member = member("Jane Doe", "jane@example.com");
    memberRepository.save(member);

    assertThat(memberRepository.findByEmail("jane@example.com")).contains(member);
  }

  @Test
  void findAllByOrderByNameAscReturnsMembersAlphabetically() {
    memberRepository.saveAll(
        List.of(member("Zoe Adams", "zoe@example.com"), member("Ava Brown", "ava@example.com")));

    assertThat(memberRepository.findAllByOrderByNameAsc())
        .extracting(Member::getName)
        .containsExactly("Ava Brown", "Zoe Adams");
  }

  @Test
  void emailIsUnique() {
    memberRepository.saveAndFlush(member("Jane Doe", "jane@example.com"));

    assertThatThrownBy(() -> memberRepository.saveAndFlush(member("Janet Doe", "jane@example.com")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private static Member member(String name, String email) {
    Member member = new Member();
    member.setName(name);
    member.setEmail(email);
    member.setPhoneNumber("2125551234");
    return member;
  }
}
