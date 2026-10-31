package com.kitchensink.controller;

import com.kitchensink.dto.MemberRequest;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.service.FeaturedMemberCacheService;
import com.kitchensink.service.MemberPaginationService;
import com.kitchensink.service.MemberPaginationService.MemberCursorPage;
import com.kitchensink.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
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
  private final MemberRepository memberRepository;
  private final MemberService memberService;
  private final FeaturedMemberCacheService featuredMemberCacheService;
  private final MemberPaginationService memberPaginationService;

  /** Creates the browser controller with member persistence and in-memory featured caching. */
  public MemberViewController(
      MemberRepository memberRepository,
      MemberService memberService,
      FeaturedMemberCacheService featuredMemberCacheService,
      MemberPaginationService memberPaginationService) {
    this.memberRepository = memberRepository;
    this.memberService = memberService;
    this.featuredMemberCacheService = featuredMemberCacheService;
    this.memberPaginationService = memberPaginationService;
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
      @RequestParam(required = false) String afterName,
      @RequestParam(required = false) String afterEmail,
      @RequestParam(required = false) String beforeName,
      @RequestParam(required = false) String beforeEmail,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String selected,
      Model model) {
    model.addAttribute("afterName", afterName);
    model.addAttribute("afterEmail", afterEmail);
    model.addAttribute("beforeName", beforeName);
    model.addAttribute("beforeEmail", beforeEmail);
    String searchEmail = email == null ? "" : email.trim();
    MemberCursorPage memberPage = null;
    List<Member> members;
    if (!searchEmail.isEmpty()) {
      Optional<Member> match = memberRepository.findByEmail(searchEmail);
      members = match.map(List::of).orElseGet(List::of);
      model.addAttribute("searchEmail", searchEmail);
      model.addAttribute("searchNotFound", match.isEmpty());
      selected = match.map(Member::getId).orElse(null);
    } else {
      memberPage =
          memberPaginationService.getPage(afterName, afterEmail, beforeName, beforeEmail);
      members = memberPage.members();
      model.addAttribute("searchEmail", "");
      model.addAttribute("searchNotFound", false);
    }

    model.addAttribute("members", members);
    model.addAttribute("cursorPage", memberPage);
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
