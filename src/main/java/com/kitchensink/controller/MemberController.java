package com.kitchensink.controller;

import com.kitchensink.dto.MemberPatchRequest;
import com.kitchensink.dto.MemberRequest;
import com.kitchensink.model.Member;
import com.kitchensink.service.MemberService;
import com.kitchensink.service.MemberService.MemberPage;
import java.net.URI;
import java.util.List;
import java.time.Duration;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import com.kitchensink.validation.Validator;

/** REST API for searching and managing members. */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
  private final MemberService memberService;
  private final MongoTemplate mongoTemplate;

  public MemberController(MemberService memberService, MongoTemplate mongoTemplate) {
    this.memberService = memberService;
    this.mongoTemplate = mongoTemplate;
    mongoTemplate.indexOps("idempotency_keys").ensureIndex(
        new Index().on("key", Sort.Direction.ASC).unique());
    mongoTemplate.indexOps("idempotency_keys").ensureIndex(
        new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofHours(24)));
  }

  @GetMapping
  public MemberListResponse search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "name") String sort,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) String cursor) {
    MemberPage page = memberService.search(q, status, sort, limit, cursor);
    return new MemberListResponse(page.data(),
        new PageMetadata(page.limit(), page.nextCursor(), page.hasMore()));
  }

  @GetMapping("/by-member-id/{memberId}")
  public Member findByMemberId(@PathVariable String memberId) {
    Member member = memberService.findByMemberId(Validator.memberId(memberId));
    if (member == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    return member;
  }

  @GetMapping("/by-email/{email:.+}")
  public Member findByEmail(@PathVariable String email) {
    return memberService.findByEmailActive(email);
  }

  @GetMapping("/{id}")
  public Member findById(@PathVariable ObjectId id) {
    Member member = memberService.findById(id);
    if (member == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    return member;
  }

  @PostMapping
  @Transactional
  public ResponseEntity<Member> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody MemberRequest request) {
    idempotencyKey = Validator.idempotencyKey(idempotencyKey);
    Document prior = mongoTemplate.findOne(
        org.springframework.data.mongodb.core.query.Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("key").is(idempotencyKey)),
        Document.class, "idempotency_keys");
    if (prior != null) {
      ObjectId memberId = prior.getObjectId("memberId");
      Member previous = memberId == null ? null : memberService.findById(memberId);
      if (previous == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request with this key is still processing");
      return ResponseEntity.created(URI.create("/api/v1/members/" + previous.getId())).body(previous);
    }
    mongoTemplate.insert(new Document("key", idempotencyKey).append("createdAt", new Date()), "idempotency_keys");
    try {
      request.validate();
      Member member = Member.create();
      member.setFirstName(request.getFirstName());
      member.setLastName(request.getLastName());
      member.setEmail(request.getEmail());
      member.setPhone(request.getPhone());
      Member created = memberService.register(member);
      mongoTemplate.updateFirst(
          org.springframework.data.mongodb.core.query.Query.query(
              org.springframework.data.mongodb.core.query.Criteria.where("key").is(idempotencyKey)),
          new org.springframework.data.mongodb.core.query.Update().set("memberId", created.getId()),
          "idempotency_keys");
      return ResponseEntity.created(URI.create("/api/v1/members/" + created.getId())).body(created);
    } catch (RuntimeException exception) {
      mongoTemplate.remove(
          org.springframework.data.mongodb.core.query.Query.query(
              org.springframework.data.mongodb.core.query.Criteria.where("key").is(idempotencyKey)),
          "idempotency_keys");
      throw exception;
    }
  }

  @PatchMapping("/{id}")
  public Member update(@PathVariable ObjectId id, @RequestBody MemberPatchRequest request) {
    return memberService.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable ObjectId id) {
    memberService.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/restore")
  public Member restore(@PathVariable ObjectId id) { return memberService.restore(id); }

  public record MemberListResponse(List<Member> data, PageMetadata page) {}
  public record PageMetadata(int limit, String nextCursor, boolean hasMore) {}
}
