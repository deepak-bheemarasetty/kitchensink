package com.kitchensink.repository;

import com.kitchensink.model.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Persistence operations for member records.
 *
 * @author Deepak Bheemarasetty
 */
public interface MemberRepository extends MongoRepository<Member, String> {
  Optional<Member> findByEmail(String email);

  List<Member> findAllByOrderByNameAsc();
}
