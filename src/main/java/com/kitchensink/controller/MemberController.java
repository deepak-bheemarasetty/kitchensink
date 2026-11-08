package com.kitchensink.controller;

import com.kitchensink.dto.MemberPatchRequest;
import com.kitchensink.dto.MemberRequest;
import com.kitchensink.dto.MemberResponse;
import com.kitchensink.model.Member;
import com.kitchensink.service.MemberService;
import com.kitchensink.service.MemberService.MemberPage;
import com.kitchensink.validation.Validator;
import java.net.URI;
import java.time.Duration;
import java.util.List;
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

/** REST API for searching and managing members. */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
  private final MemberService memberService;
  private final MongoTemplate mongoTemplate;

  /**
   * Constructs a new member controller.
   *
   * @param memberService member service for business logic
   * @param mongoTemplate mongo template for persistence operations
   */
  public MemberController(MemberService memberService, MongoTemplate mongoTemplate) {
    this.memberService = memberService;
    this.mongoTemplate = mongoTemplate;
    mongoTemplate
        .indexOps("idempotency_keys")
        .createIndex(new Index().on("key", Sort.Direction.ASC).unique());
    mongoTemplate
        .indexOps("idempotency_keys")
        .createIndex(new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofHours(24)));
  }

  /**
   * Searches members with cursor-based pagination and filtering.
   *
   * @param q optional search term
   * @param status optional status filter
   * @param sort sort field
   * @param limit maximum page size
   * @param cursor cursor token
   * @return paginated response containing members and page metadata
   */
  @GetMapping
  public MemberListResponse search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "name") String sort,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) String cursor) {
    MemberPage page = memberService.search(q, status, sort, limit, cursor);
    List<MemberResponse> responseList = page.data().stream().map(MemberResponse::from).toList();
    return new MemberListResponse(
        responseList, new PageMetadata(page.limit(), page.nextCursor(), page.hasMore()));
  }

  /**
   * Finds a member by formatted member identifier.
   *
   * @param memberId formatted member identifier
   * @return matching member response
   */
  @GetMapping("/by-member-id/{memberId}")
  public MemberResponse findByMemberId(@PathVariable String memberId) {
    Member member = memberService.findByMemberId(Validator.memberId(memberId));
    if (member == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    }
    return MemberResponse.from(member);
  }

  @GetMapping("/by-email/{email:.+}")
  public MemberResponse findByEmail(@PathVariable String email) {
    Member member = memberService.findByEmailActive(Validator.email(email));
    return MemberResponse.from(member);
  }

  /**
   * Finds a member by primary key identifier.
   *
   * @param id member object id
   * @return matching member response
   */
  @GetMapping("/{id}")
  public MemberResponse findById(@PathVariable ObjectId id) {
    Member member = memberService.findById(id);
    if (member == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    }
    return MemberResponse.from(member);
  }

  /**
   * Creates a new member with idempotency guarantee.
   *
   * @param idempotencyKey unique key to ensure idempotency
   * @param request member registration payload
   * @return created member response with location header
   */
  @PostMapping
  public ResponseEntity<MemberResponse> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody MemberRequest request) {
    request.validate();
    Member member = Member.create();
    member.setFirstName(request.getFirstName());
    member.setLastName(request.getLastName());
    member.setEmail(request.getEmail());
    member.setCountryCode(request.getCountryCode());
    member.setPhone(request.getPhone());
    Member created = memberService.register(member, idempotencyKey);
    return ResponseEntity.created(URI.create("/api/v1/members/" + created.getId()))
        .body(MemberResponse.from(created));
  }

  @PatchMapping("/{id}")
  public MemberResponse update(@PathVariable ObjectId id, @RequestBody MemberPatchRequest request) {
    return MemberResponse.from(memberService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable ObjectId id) {
    memberService.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/restore")
  public MemberResponse restore(@PathVariable ObjectId id) {
    return MemberResponse.from(memberService.restore(id));
  }

  /** Response representation for paginated member searches. */
  public record MemberListResponse(List<MemberResponse> data, PageMetadata page) {}

  /** Pagination metadata including limits and cursor position. */
  public record PageMetadata(int limit, String nextCursor, boolean hasMore) {}
}
