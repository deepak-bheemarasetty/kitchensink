package com.kitchensink.repository;

import com.kitchensink.model.Member;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Persistence operations for members. */
public interface MemberRepository extends MongoRepository<Member, ObjectId> {
  Optional<Member> findByEmail(String email);
}
