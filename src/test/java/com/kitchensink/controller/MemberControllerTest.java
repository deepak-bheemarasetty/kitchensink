package com.kitchensink.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.DuplicateEmailException;
import com.kitchensink.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
class MemberControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private MemberService memberService;

    @Test
    void listMembersReturnsMembersOrderedByName() throws Exception {
        Member member = member("Jane Doe", "jane@example.com");
        when(memberRepository.findAllByOrderByNameAsc()).thenReturn(List.of(member));

        mockMvc.perform(get("/rest/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Jane Doe"));
    }

    @Test
    void getMemberReturnsMemberWhenPresent() throws Exception {
        Member member = member("Jane Doe", "jane@example.com");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        mockMvc.perform(get("/rest/members/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
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

        mockMvc.perform(post("/rest/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/rest/members/7"))
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void createMemberWithMissingNameReturnsFieldValidationError() throws Exception {
        mockMvc.perform(post("/rest/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","phoneNumber":"2125551234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void createMemberWithInvalidPhoneReturnsFieldValidationError() throws Exception {
        mockMvc.perform(post("/rest/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.phoneNumber").exists());
    }

    @Test
    void createMemberWithDuplicateEmailReturnsConflict() throws Exception {
        when(memberService.register(any(Member.class)))
                .thenThrow(new DuplicateEmailException("Email already registered"));

        mockMvc.perform(post("/rest/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    @Test
    void createMemberWithOtherDataIntegrityConflictReturnsGenericConflict() throws Exception {
        when(memberService.register(any(Member.class)))
                .thenThrow(new DataIntegrityViolationException("database detail"));

        mockMvc.perform(post("/rest/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
