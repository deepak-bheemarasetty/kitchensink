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

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.service.MemberService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the member REST endpoints and request validation.
 *
 * @author Deepak Bheemarasetty
 */
@WebMvcTest(MemberController.class)
class MemberControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberRepository memberRepository;

  @MockitoBean private MemberService memberService;

  @Test
  void listMembersReturnsMembersOrderedByName() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId(0L);
    when(memberRepository.findAllByOrderByNameAsc()).thenReturn(List.of(member));

    mockMvc
        .perform(get("/rest/members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(0))
        .andExpect(jsonPath("$[0].name").value("Jane Doe"))
        .andExpect(jsonPath("$[0].email").value("jane@example.com"))
        .andExpect(jsonPath("$[0].phoneNumber").value("2125551234"))
        .andExpect(jsonPath("$[0].password").doesNotExist());
  }

  @Test
  void getMemberReturnsMemberWhenPresent() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId(7L);
    when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

    mockMvc
        .perform(get("/rest/members/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.name").value("Jane Doe"))
        .andExpect(jsonPath("$.email").value("jane@example.com"))
        .andExpect(jsonPath("$.phoneNumber").value("2125551234"));
  }

  @Test
  void getMemberReturnsNotFoundWhenMissing() throws Exception {
    when(memberRepository.findById(999L)).thenReturn(Optional.empty());

    mockMvc.perform(get("/rest/members/999")).andExpect(status().isNotFound());
  }

  @Test
  void createMemberReturnsCreatedAndLocation() throws Exception {
    Member member = member("Jane Doe", "jane@example.com");
    member.setId(7L);
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
        .andExpect(header().string("Location", "http://localhost/rest/members/7"))
        .andExpect(jsonPath("$.id").value(7))
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
