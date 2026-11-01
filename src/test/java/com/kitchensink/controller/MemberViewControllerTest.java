package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.kitchensink.config.SecurityConfiguration;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.service.FeaturedMemberCacheService;
import com.kitchensink.service.FeaturedMemberCacheService.CachedMember;
import com.kitchensink.service.MemberPaginationService;
import com.kitchensink.service.MemberPaginationService.MemberCursorPage;
import com.kitchensink.service.MemberService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the browser member management pages. */
@WebMvcTest(MemberViewController.class)
@Import(SecurityConfiguration.class)
@WithMockUser
class MemberViewControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberRepository memberRepository;

  @MockitoBean private MemberService memberService;

  @MockitoBean private FeaturedMemberCacheService featuredMemberCacheService;

  @MockitoBean private MemberPaginationService memberPaginationService;

  @Test
  @WithAnonymousUser
  void anonymousRequestToBrowserUiRedirectsToOidcLogin() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  @WithAnonymousUser
  void loginPageOffersAuth0SignIn() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Auth0")))
        .andExpect(content().string(containsString("/oauth2/authorization/auth0")));
  }

  @Test
  void homeShowsGreetingAndFindAddPanesWithCachedMostViewedMembers() throws Exception {
    when(featuredMemberCacheService.getFeaturedMembers())
        .thenReturn(List.of(new CachedMember("member-1", "Jane Doe", "jane@example.com", 3)));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(model().attributeExists("member", "featuredMembers", "username"))
        .andExpect(model().attribute("username", "user"))
        .andExpect(content().string(containsString("Jane Doe")))
        .andExpect(content().string(containsString("Most Viewed Members")))
        .andExpect(content().string(containsString("Enter the full email for search")))
        .andExpect(content().string(containsString("name=\"_csrf\"")))
        .andExpect(content().string(containsString("View All Members")))
        .andExpect(content().string(containsString("Welcome")));

    verify(memberPaginationService, never()).getSummaryPage(null, null, null, null);
  }

  @Test
  void homeSearchShowsExactEmailMatchAndSelectedDetailsDialog() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("member-1");
    when(memberRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(member));
    when(featuredMemberCacheService.recordView("member-1")).thenReturn(Optional.of(member));
    when(featuredMemberCacheService.getFeaturedMembers()).thenReturn(List.of());

    mockMvc
        .perform(
            get("/")
                .param("searchEmail", " jane@example.com ")
                .param("selected", "member-1"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("searchEmail", "jane@example.com"))
        .andExpect(model().attribute("searchResult", member))
        .andExpect(content().string(containsString("Search Results")))
        .andExpect(content().string(containsString("id=\"member-details-dialog\"")))
        .andExpect(content().string(containsString("2125551234")));

    verify(memberRepository).findByEmail("jane@example.com");
    verify(featuredMemberCacheService).recordView("member-1");
  }

  @Test
  void homeSearchShowsNotFoundMessageAndDoesNotShowDetailsForUnknownMember()
      throws Exception {
    when(memberRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
    when(featuredMemberCacheService.getFeaturedMembers()).thenReturn(List.of());
    when(featuredMemberCacheService.recordView("unknown-member")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/")
                .param("searchEmail", "missing@example.com")
                .param("selected", "unknown-member"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("searchNotFound", true))
        .andExpect(
            content().string(containsString("No member found with that full email address.")))
        .andExpect(content().string(containsString("id=\"emailSearch\"")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(containsString("member-details-dialog"))));
  }

  @Test
  void allMembersPageShowsOnlyNameEmailAndDetailsLink() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("member-1");
    when(memberPaginationService.getSummaryPage(null, null, null, null))
        .thenReturn(new MemberCursorPage(List.of(member), false, false));

    mockMvc
        .perform(get("/members"))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"))
        .andExpect(model().attributeExists("members", "cursorPage"))
        .andExpect(content().string(containsString("Jane Doe")))
        .andExpect(content().string(containsString("jane@example.com")))
        .andExpect(content().string(containsString("Member Details")));

    String content =
        mockMvc.perform(get("/members")).andReturn().getResponse().getContentAsString();
    assertThat(content).doesNotContain("2125551234");
  }

  @Test
  void allMembersPageUsesTwentyMemberPagesAndNavigation() throws Exception {
    Member member = member("Zoe Doe", "zoe@example.com");
    member.setId("member-21");
    when(memberPaginationService.getSummaryPage("Taylor", "t@example.com", null, null))
        .thenReturn(new MemberCursorPage(List.of(member), true, true));

    mockMvc
        .perform(get("/members").param("afterName", "Taylor").param("afterEmail", "t@example.com"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Zoe Doe")))
        .andExpect(content().string(containsString("beforeName=Zoe")))
        .andExpect(content().string(containsString("afterName=Zoe")));

    verify(memberPaginationService).getSummaryPage("Taylor", "t@example.com", null, null);
  }

  @Test
  void selectingMemberShowsDetailsInRightPaneWithoutLeavingMembersPage() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("member-1");
    when(memberPaginationService.getSummaryPage(null, null, null, null))
        .thenReturn(new MemberCursorPage(List.of(member), false, false));
    when(featuredMemberCacheService.recordView("member-1")).thenReturn(Optional.of(member));

    mockMvc
        .perform(
            get("/members")
                .param("selected", "member-1")
                .param("email", "")
                .param("afterName", "")
                .param("afterEmail", "")
                .param("beforeName", "")
                .param("beforeEmail", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"))
        .andExpect(content().string(containsString("2125551234")))
        .andExpect(content().string(containsString("Jane Doe")));

    verify(featuredMemberCacheService).recordView("member-1");
  }

  @Test
  void searchingByEmailShowsOnlyMatchingMemberAndItsDetails() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("member-1");
    when(memberRepository.findByEmail("jane@example.com"))
        .thenReturn(Optional.of(member));
    when(featuredMemberCacheService.recordView("member-1")).thenReturn(Optional.of(member));

    mockMvc
        .perform(get("/members").param("email", "jane@example.com"))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"))
        .andExpect(content().string(containsString("Jane Doe")))
        .andExpect(content().string(containsString("2125551234")))
        .andExpect(content().string(containsString("Clear Search")));

    verify(memberRepository).findByEmail("jane@example.com");
    verify(memberPaginationService, never()).getSummaryPage(null, null, null, null);
  }

  @Test
  void unknownEmailShowsDismissibleWarningAndClearSearchLink() throws Exception {
    when(memberRepository.findByEmail("missing@example.com"))
        .thenReturn(Optional.empty());

    mockMvc
        .perform(get("/members").param("email", "missing@example.com"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Member Not Found")))
        .andExpect(content().string(containsString("href=\"/members\"")))
        .andExpect(content().string(containsString("missing@example.com")));
  }

  @Test
  void clearingSearchLoadsTheFullPagedMemberList() throws Exception {
    when(memberPaginationService.getSummaryPage(null, null, null, null))
        .thenReturn(new MemberCursorPage(List.of(), false, false));

    mockMvc
        .perform(get("/members"))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"));

    verify(memberPaginationService).getSummaryPage(null, null, null, null);
  }

  @Test
  void createMemberRedirectsHomeAfterRegistration() throws Exception {
    Member savedMember = member("Jane Doe", "jane@example.com");
    savedMember.setId("member-1");
    when(memberService.register(any(Member.class))).thenReturn(savedMember);

    var result =
        mockMvc
            .perform(
                post("/members")
                    .with(csrf())
                    .param("name", "Jane Doe")
                    .param("email", "jane@example.com")
                    .param("phoneNumber", "2125551234"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn();

    assertThat(result.getFlashMap().get("successMessage"))
        .isEqualTo("Member created successfully.");
    assertThat(result.getFlashMap().get("createdMember")).isEqualTo(savedMember);
    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    verify(memberService).register(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getName()).isEqualTo("Jane Doe");
    assertThat(memberCaptor.getValue().getEmail()).isEqualTo("jane@example.com");
    assertThat(memberCaptor.getValue().getPhoneNumber()).isEqualTo("2125551234");
  }

  @Test
  void createMemberWithValidationErrorsReturnsHomePage() throws Exception {
    when(featuredMemberCacheService.getFeaturedMembers()).thenReturn(List.of());

    mockMvc
        .perform(
            post("/members")
                .with(csrf())
                .param("name", "Jane1")
                .param("email", "invalid")
                .param("phoneNumber", "123"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(model().attributeHasFieldErrors("member", "name", "email", "phoneNumber"));

    verify(memberService, never()).register(any(Member.class));
  }

  @Test
  void createMemberWithDuplicateEmailShowsErrorOnHomePage() throws Exception {
    when(memberService.register(any(Member.class)))
        .thenThrow(new DuplicateEmailException("Email already registered: jane@example.com"));
    when(featuredMemberCacheService.getFeaturedMembers()).thenReturn(List.of());

    mockMvc
        .perform(
            post("/members")
                .with(csrf())
                .param("name", "Jane Doe")
                .param("email", "jane@example.com")
                .param("phoneNumber", "2125551234"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(model().attributeExists("registrationError"))
        .andExpect(
            content().string(containsString("Email already registered: jane@example.com")));
  }

  @Test
  void browserRegistrationRejectsMissingCsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/members")
                .param("name", "Jane Doe")
                .param("email", "jane@example.com")
                .param("phoneNumber", "2125551234"))
        .andExpect(status().isForbidden());

    verify(memberService, never()).register(any(Member.class));
  }

  private static Member member(String name, String email) {
    Member member = new Member();
    member.setName(name);
    member.setEmail(email);
    member.setPhoneNumber("2125551234");
    return member;
  }
}
