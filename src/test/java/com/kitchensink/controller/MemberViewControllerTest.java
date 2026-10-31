package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.kitchensink.service.MemberService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

  @Test
  @WithAnonymousUser
  void anonymousRequestToBrowserUiIsRejected() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
  }

  @Test
  void homeShowsRegistrationFormAndCachedMostViewedMembers() throws Exception {
    when(featuredMemberCacheService.getFeaturedMembers())
        .thenReturn(List.of(new CachedMember("member-1", "Jane Doe", "jane@example.com", 3)));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(model().attributeExists("member", "featuredMembers"))
        .andExpect(content().string(containsString("Jane Doe")))
        .andExpect(content().string(containsString("Register a Member")))
        .andExpect(content().string(containsString("Show All Members")));

    verify(memberRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void allMembersPageShowsOnlyNameEmailAndDetailsLink() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("member-1");
    when(memberRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/members"))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"))
        .andExpect(model().attributeExists("memberPage"))
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
    when(memberRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(1, 20), 41));

    mockMvc
        .perform(get("/members").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Zoe Doe")))
        .andExpect(content().string(containsString("page=0")))
        .andExpect(content().string(containsString("page=2")));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Mockito.verify(memberRepository).findAll(pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageableCaptor.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
  }

  @Test
  void selectingMemberShowsDetailsInRightPaneWithoutLeavingMembersPage() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("member-1");
    when(memberRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));
    when(featuredMemberCacheService.recordView("member-1")).thenReturn(Optional.of(member));

    mockMvc
        .perform(get("/members").param("selected", "member-1"))
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
    when(memberRepository.findByEmailIgnoreCase("jane@example.com"))
        .thenReturn(Optional.of(member));
    when(featuredMemberCacheService.recordView("member-1")).thenReturn(Optional.of(member));

    mockMvc
        .perform(get("/members").param("email", "jane@example.com"))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"))
        .andExpect(content().string(containsString("Jane Doe")))
        .andExpect(content().string(containsString("2125551234")))
        .andExpect(content().string(containsString("Clear Search")));

    verify(memberRepository).findByEmailIgnoreCase("jane@example.com");
    verify(memberRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void unknownEmailShowsDismissibleWarningAndClearSearchLink() throws Exception {
    when(memberRepository.findByEmailIgnoreCase("missing@example.com"))
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
    when(memberRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/members"))
        .andExpect(status().isOk())
        .andExpect(view().name("all-members"));

    verify(memberRepository).findAll(any(Pageable.class));
  }

  @Test
  void createMemberRedirectsHomeAfterRegistration() throws Exception {
    Member savedMember = member("Jane Doe", "jane@example.com");
    savedMember.setId("member-1");
    when(memberService.register(any(Member.class))).thenReturn(savedMember);

    mockMvc
        .perform(
            post("/members")
                .param("name", "Jane Doe")
                .param("email", "jane@example.com")
                .param("phoneNumber", "2125551234"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

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
                .param("name", "Jane Doe")
                .param("email", "jane@example.com")
                .param("phoneNumber", "2125551234"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(model().attributeHasFieldErrorCode("member", "email", "duplicate.email"));
  }

  private static Member member(String name, String email) {
    Member member = new Member();
    member.setName(name);
    member.setEmail(email);
    member.setPhoneNumber("2125551234");
    return member;
  }
}
