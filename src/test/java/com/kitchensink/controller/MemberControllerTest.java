package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.config.SecurityConfiguration;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.service.MemberPaginationService;
import com.kitchensink.service.MemberPaginationService.MemberCursorPage;
import com.kitchensink.service.MemberService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests the member REST endpoints and request validation.
 *
 * @author Deepak Bheemarasetty
 */
@WebMvcTest(MemberController.class)
@Import(SecurityConfiguration.class)
@WithMockUser
class MemberControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberRepository memberRepository;

  @MockitoBean private MemberPaginationService memberPaginationService;

  @MockitoBean private MemberService memberService;

  @Test
  void listMembersReturnsBoundedCursorPage() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("507f1f77bcf86cd799439011");
    when(memberPaginationService.getPage(null, null, null, null))
        .thenReturn(new MemberCursorPage(List.of(member), false, true));

    mockMvc
        .perform(get("/rest/members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[0].id").value("507f1f77bcf86cd799439011"))
        .andExpect(jsonPath("$.members[0].name").value("Jane Doe"))
        .andExpect(jsonPath("$.members[0].email").value("jane@example.com"))
        .andExpect(jsonPath("$.members[0].phoneNumber").value("2125551234"))
        .andExpect(jsonPath("$.members[0].password").doesNotExist())
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor.name").value("Jane Doe"))
        .andExpect(jsonPath("$.nextCursor.email").value("jane@example.com"));
  }

  @Test
  void listMembersPassesForwardCursorToPaginationService() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("507f1f77bcf86cd799439011");
    when(memberPaginationService.getPage("Jane Doe", "jane@example.com", null, null))
        .thenReturn(new MemberCursorPage(List.of(member), true, false));

    mockMvc
        .perform(
            get("/rest/members")
                .param("afterName", "Jane Doe")
                .param("afterEmail", "jane@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasPrevious").value(true))
        .andExpect(jsonPath("$.previousCursor.name").value("Jane Doe"));

    verify(memberPaginationService).getPage("Jane Doe", "jane@example.com", null, null);
  }

  @Test
  void listMembersRejectsIncompleteCursor() throws Exception {
    when(memberPaginationService.getPage("Jane Doe", null, null, null))
        .thenThrow(new ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid cursor"));

    mockMvc
        .perform(get("/rest/members").param("afterName", "Jane Doe"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getMemberReturnsMemberWhenPresent() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("507f1f77bcf86cd799439017");
    when(memberRepository.findById("507f1f77bcf86cd799439017")).thenReturn(Optional.of(member));

    mockMvc
        .perform(get("/rest/members/507f1f77bcf86cd799439017"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("507f1f77bcf86cd799439017"))
        .andExpect(jsonPath("$.name").value("Jane Doe"))
        .andExpect(jsonPath("$.email").value("jane@example.com"))
        .andExpect(jsonPath("$.phoneNumber").value("2125551234"));
  }

  @Test
  void getMemberReturnsNotFoundWhenMissing() throws Exception {
    when(memberRepository.findById("507f1f77bcf86cd799439999")).thenReturn(Optional.empty());

    mockMvc.perform(get("/rest/members/507f1f77bcf86cd799439999")).andExpect(status().isNotFound());
  }

  @Test
  void createMemberReturnsCreatedAndLocation() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId("507f1f77bcf86cd799439017");
    when(memberService.register(any(Member.class))).thenReturn(member);

    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            header().string(
                "Location", "http://localhost/rest/members/507f1f77bcf86cd799439017"))
        .andExpect(jsonPath("$.id").value("507f1f77bcf86cd799439017"))
        .andExpect(jsonPath("$.name").value("Jane Doe"))
        .andExpect(jsonPath("$.email").value("jane@example.com"))
        .andExpect(jsonPath("$.phoneNumber").value("2125551234"));

    var memberCaptor = forClass(Member.class);
    verify(memberService).register(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getName()).isEqualTo("Jane Doe");
    assertThat(memberCaptor.getValue().getEmail()).isEqualTo("jane@example.com");
    assertThat(memberCaptor.getValue().getPhoneNumber()).isEqualTo("2125551234");
  }

  @Test
  @WithAnonymousUser
  void anonymousRequestIsRejected() throws Exception {
    mockMvc.perform(get("/rest/members")).andExpect(status().isUnauthorized());
  }

  @Test
  void createMemberWithMissingNameReturnsFieldValidationError() throws Exception {
    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"jane@example.com","phoneNumber":"2125551234"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.name").exists());
  }

  @Test
  void createMemberWithInvalidPhoneReturnsFieldValidationError() throws Exception {
    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"123"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.phoneNumber").exists());
  }

  @Test
  void createMemberWithDigitsInNameReturnsResolvedValidationMessage() throws Exception {
    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Jane1","email":"jane@example.com","phoneNumber":"2125551234"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.name").value("Name must not contain digits or special characters"));
  }

  @Test
  void createMemberWithSpecialCharactersInNameReturnsValidationError() throws Exception {
    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Jane-Doe!","email":"jane@example.com","phoneNumber":"2125551234"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.name").value("Name must not contain digits or special characters"));
  }

  @Test
  void createMemberWithDuplicateEmailReturnsConflict() throws Exception {
    when(memberService.register(any(Member.class)))
        .thenThrow(new DuplicateEmailException("Email already registered"));

    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.email").value("Email taken"));
  }

  @Test
  void createMemberWithOtherDataIntegrityConflictReturnsGenericConflict() throws Exception {
    when(memberService.register(any(Member.class)))
        .thenThrow(new DataIntegrityViolationException("database detail"));

    mockMvc
        .perform(
            post("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("The request conflicts with existing data"));
  }

  private static Member member(String name, String email) {
    Member member = new Member();
    member.setName(name);
    member.setEmail(email);
    member.setPhoneNumber("2125551234");
    return member;
  }
}
