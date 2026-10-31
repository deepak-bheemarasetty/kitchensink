package com.kitchensink.controller;

import com.kitchensink.dto.MemberRequest;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  private final MemberRepository memberRepository;
  private final MemberService memberService;

  public MemberController(MemberRepository memberRepository, MemberService memberService) {
    this.memberRepository = memberRepository;
    this.memberService = memberService;
  }

  /** Lists all members in ascending name order. */
  @GetMapping
  public List<MemberResponse> listAllMembers() {
    return memberRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
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
