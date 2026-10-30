package com.kitchensink.repository;

import com.kitchensink.model.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence operations for member records.
 *
 * @author Deepak Bheemarasetty
 */
public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByEmail(String email);

  List<Member> findAllByOrderByNameAsc();
}
