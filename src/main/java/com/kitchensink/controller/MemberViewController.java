package com.kitchensink.controller;

import com.kitchensink.dto.MemberRequest;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.service.FeaturedMemberCacheService;
import com.kitchensink.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Renders the browser-based member management pages. */
@Controller
public class MemberViewController {
  private static final int PAGE_SIZE = 20;

  private final MemberRepository memberRepository;
  private final MemberService memberService;
  private final FeaturedMemberCacheService featuredMemberCacheService;

  /** Creates the browser controller with member persistence and in-memory featured caching. */
  public MemberViewController(
      MemberRepository memberRepository,
      MemberService memberService,
      FeaturedMemberCacheService featuredMemberCacheService) {
    this.memberRepository = memberRepository;
    this.memberService = memberService;
    this.featuredMemberCacheService = featuredMemberCacheService;
  }

  /** Shows the registration form and cached most-viewed members. */
  @GetMapping("/")
  public String showHome(Model model) {
    model.addAttribute("member", new MemberRequest("", "", ""));
    populateHomeModel(model);
    return "index";
  }

  /** Lists all members in the browser UI. */
  @GetMapping("/members")
  public String listMembers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String selected,
      Model model) {
    String searchEmail = email == null ? "" : email.trim();
    Page<Member> members;
    if (!searchEmail.isEmpty()) {
      Optional<Member> match = memberRepository.findByEmailIgnoreCase(searchEmail);
      members = new PageImpl<>(match.map(List::of).orElseGet(List::of));
      model.addAttribute("searchEmail", searchEmail);
      model.addAttribute("searchNotFound", match.isEmpty());
      selected = match.map(Member::getId).orElse(null);
    } else {
      members =
          memberRepository.findAll(
              PageRequest.of(
                  Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.ASC, "name")));
      model.addAttribute("searchEmail", "");
      model.addAttribute("searchNotFound", false);
    }

    model.addAttribute("memberPage", members);
    model.addAttribute(
        "selectedMember",
        selected == null ? null : featuredMemberCacheService.recordView(selected).orElse(null));
    return "all-members";
  }

  /** Saves a new member from the home-page form. */
  @PostMapping("/members")
  public String createMember(
      @Valid @ModelAttribute("member") MemberRequest request,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      populateHomeModel(model);
      return "index";
    }

    try {
      memberService.register(toEntity(request));
      redirectAttributes.addFlashAttribute("successMessage", "Member created successfully.");
      return "redirect:/";
    } catch (DuplicateEmailException e) {
      bindingResult.rejectValue("email", "duplicate.email", e.getMessage());
      populateHomeModel(model);
      return "index";
    }
  }

  private void populateHomeModel(Model model) {
    model.addAttribute("featuredMembers", featuredMemberCacheService.getFeaturedMembers());
  }

  private Member toEntity(MemberRequest request) {
    Member member = new Member();
    member.setName(request.name());
    member.setEmail(request.email());
    member.setPhoneNumber(request.phoneNumber());
    return member;
  }
}
