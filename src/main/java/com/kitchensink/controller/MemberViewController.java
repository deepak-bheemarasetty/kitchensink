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
import java.security.Principal;
import java.util.Locale;
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

  /** Shows the sign-in page before redirecting the user to Auth0. */
  @GetMapping("/login")
  public String showLogin() {
    return "login";
  }

  /** Shows member search, cached most-viewed members, and the registration form. */
  @GetMapping("/")
  public String showHome(
      @RequestParam(required = false) String searchEmail,
      @RequestParam(required = false) String selected,
      Principal principal,
      Model model) {
    model.addAttribute("member", new MemberRequest("", "", ""));
    String normalizedSearchEmail =
        searchEmail == null ? "" : searchEmail.trim().toLowerCase(Locale.ROOT);
    Optional<Member> searchResult =
        normalizedSearchEmail.isEmpty()
            ? Optional.empty()
            : memberRepository.findByEmailIgnoreCase(normalizedSearchEmail);
    searchResult.ifPresent(featuredMemberCacheService::recordView);
    model.addAttribute("username", principal.getName());
    model.addAttribute("searchEmail", normalizedSearchEmail);
    model.addAttribute("searchResult", searchResult.orElse(null));
    model.addAttribute(
        "searchNotFound", !normalizedSearchEmail.isEmpty() && searchResult.isEmpty());
    model.addAttribute("featuredMembers", featuredMemberCacheService.getFeaturedMembers());
    model.addAttribute(
        "selectedMember",
        selected == null ? null : featuredMemberCacheService.recordView(selected).orElse(null));
    return "index";
  }

  /** Lists all members in the browser UI. */
  @GetMapping("/members")
  public String listMembers(
      @RequestParam(required = false) String afterEmail,
      @RequestParam(required = false) String beforeEmail,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String selected,
      Model model) {
    afterEmail = normalizeCursor(afterEmail);
    beforeEmail = normalizeCursor(beforeEmail);
    model.addAttribute("afterEmail", afterEmail);
    model.addAttribute("beforeEmail", beforeEmail);
    String searchEmail = email == null ? "" : email.trim();
    MemberCursorPage memberPage =
        memberPaginationService.getSummaryPage(afterEmail, beforeEmail, searchEmail);
    model.addAttribute("members", memberPage.members());
    model.addAttribute("cursorPage", memberPage);
    model.addAttribute("searchEmail", searchEmail);
    model.addAttribute("searchNotFound", !searchEmail.isEmpty() && memberPage.members().isEmpty());
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
      Principal principal,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      populateHomeModel(model, principal);
      return "index";
    }

    try {
      Member createdMember = memberService.register(toEntity(request));
      featuredMemberCacheService.recordView(createdMember);
      redirectAttributes.addFlashAttribute("successMessage", "Member created successfully.");
      redirectAttributes.addFlashAttribute("createdMember", createdMember);
      return "redirect:/";
    } catch (DuplicateEmailException e) {
      model.addAttribute("registrationError", e.getMessage());
      populateHomeModel(model, principal);
      return "index";
    }
  }

  private void populateHomeModel(Model model, Principal principal) {
    model.addAttribute("username", principal.getName());
    model.addAttribute("searchEmail", "");
    model.addAttribute("searchResult", null);
    model.addAttribute("searchNotFound", false);
    model.addAttribute("featuredMembers", featuredMemberCacheService.getFeaturedMembers());
  }

  private static String normalizeCursor(String cursor) {
    return cursor == null || cursor.isBlank() ? null : cursor;
  }

  private Member toEntity(MemberRequest request) {
    Member member = new Member();
    member.setName(request.name());
    member.setEmail(request.email());
    member.setPhoneNumber(request.phoneNumber());
    return member;
  }
}
