package com.kitchensink.controller;

import com.kitchensink.dto.MemberRequest;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.MemberPaginationService;
import com.kitchensink.service.MemberPaginationService.MemberCursorPage;
import com.kitchensink.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Exposes REST endpoints for member registration and lookup.
 *
 * @author Deepak Bheemarasetty
 */
@RestController
@RequestMapping("/rest/members")
public class MemberController {
  /**
   * Public representation of a member returned by this API.
   *
   * @author Deepak Bheemarasetty
   */
  public record MemberResponse(String id, String name, String email, String phoneNumber) {}

  /** Cursor links for moving through a bounded member-list response. */
  public record MemberCursor(String email) {}

  /** A bounded member page and cursor values for adjacent requests. */
  public record MemberPageResponse(
      List<MemberResponse> members,
      boolean hasPrevious,
      boolean hasNext,
      MemberCursor previousCursor,
      MemberCursor nextCursor) {}

  private final MemberPaginationService memberPaginationService;
  private final MemberRepository memberRepository;
  private final MemberService memberService;

  /** Creates the REST controller with bounded member pagination and registration services. */
  public MemberController(
      MemberPaginationService memberPaginationService,
      MemberRepository memberRepository,
      MemberService memberService) {
    this.memberPaginationService = memberPaginationService;
    this.memberRepository = memberRepository;
    this.memberService = memberService;
  }

  /** Lists a bounded member page using forward or backward keyset cursors. */
  @GetMapping
  public MemberPageResponse listAllMembers(
      @RequestParam(required = false) String afterEmail,
      @RequestParam(required = false) String beforeEmail) {
    MemberCursorPage page =
        memberPaginationService.getPage(afterEmail, beforeEmail);
    return new MemberPageResponse(
        page.members().stream().map(this::toResponse).toList(),
        page.hasPrevious(),
        page.hasNext(),
        page.hasPrevious() ? new MemberCursor(page.previousEmail()) : null,
        page.hasNext() ? new MemberCursor(page.nextEmail()) : null);
  }

  /** Looks up a member by its identifier. */
  @GetMapping("/{id}")
  public ResponseEntity<MemberResponse> lookupMemberById(@PathVariable("id") String id) {
    return memberRepository
        .findById(id)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Validates and registers a member, returning its resource location. */
  @PostMapping
  public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody MemberRequest request) {
    Member created = memberService.register(toEntity(request));
    return ResponseEntity.created(
            ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri())
        .body(toResponse(created));
  }

  private Member toEntity(MemberRequest request) {
    Member member = new Member();
    member.setName(request.name());
    member.setEmail(request.email());
    member.setPhoneNumber(request.phoneNumber());
    return member;
  }

  private MemberResponse toResponse(Member member) {
    return new MemberResponse(
        member.getId(), member.getName(), member.getEmail(), member.getPhoneNumber());
  }
}
