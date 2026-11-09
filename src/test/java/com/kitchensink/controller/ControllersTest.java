package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.MemberPatchRequest;
import com.kitchensink.dto.MemberRequest;
import com.kitchensink.dto.MemberResponse;
import com.kitchensink.model.Member;
import com.kitchensink.service.IdentityResolver;
import com.kitchensink.service.MemberService;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for MemberController and CurrentUserController.
 *
 * @author Deepak Bheemarasetty
 */
class ControllersTest {

  private MemberService memberService;
  private MongoTemplate mongoTemplate;
  private IdentityResolver identityResolver;
  private MemberController memberController;
  private CurrentUserController currentUserController;

  @BeforeEach
  void setUp() {
    memberService = mock(MemberService.class);
    mongoTemplate = mock(MongoTemplate.class);
    identityResolver = mock(IdentityResolver.class);
    IndexOperations indexOps = mock(IndexOperations.class);
    when(mongoTemplate.indexOps("idempotency_keys")).thenReturn(indexOps);

    memberController = new MemberController(memberService, mongoTemplate);
    currentUserController = new CurrentUserController(identityResolver);
  }

  @Test
  void memberControllerSearch() {
    Member m = Member.create();
    m.setId(new ObjectId());
    m.setFirstName("Alice");
    m.setEmail("alice@example.com");
    m.setPhone("2125551212");

    when(memberService.search(any(), any(), any(), eq(50), any()))
        .thenReturn(new MemberService.MemberPage(List.of(m), 50, null, false));

    var response = memberController.search(null, null, "name", 50, null);
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).getFirstName()).isEqualTo("Alice");
  }

  @Test
  void memberControllerFindByIdAndMemberId() {
    ObjectId id = new ObjectId();
    Member m = Member.create();
    m.setId(id);
    m.setMemberId("M-100");
    m.setFirstName("Bob");
    m.setEmail("bob@example.com");
    m.setPhone("2125551212");

    when(memberService.findById(id)).thenReturn(m);
    when(memberService.findByMemberId("M-100")).thenReturn(m);
    when(memberService.findByEmailActive("bob@example.com")).thenReturn(m);

    assertThat(memberController.findById(id).getFirstName()).isEqualTo("Bob");
    assertThat(memberController.findByMemberId("M-100").getFirstName()).isEqualTo("Bob");
    assertThat(memberController.findByEmail("bob@example.com").getFirstName()).isEqualTo("Bob");

    when(memberService.findById(id)).thenReturn(null);
    assertThatThrownBy(() -> memberController.findById(id))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void memberControllerCreateUpdateDeleteRestore() {
    Member created = Member.create();
    created.setId(new ObjectId());
    created.setFirstName("Sam");
    created.setEmail("sam@example.com");
    created.setPhone("2125551212");

    when(memberService.register(any(), eq("key-1"))).thenReturn(created);

    MemberRequest req = MemberRequest.of("Sam", "Lee", "sam@example.com", "1", "2125551212");
    ResponseEntity<MemberResponse> res = memberController.create("key-1", req);
    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(res.getBody().getFirstName()).isEqualTo("Sam");

    when(memberService.update(eq(created.getId()), any())).thenReturn(created);
    assertThat(memberController.update(created.getId(), new MemberPatchRequest())).isNotNull();

    memberController.delete(created.getId());
    verify(memberService).softDelete(created.getId());

    when(memberService.restore(created.getId())).thenReturn(created);
    memberController.restore(created.getId());
    verify(memberService).restore(created.getId());
  }

  @Test
  void currentUserControllerTest() {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(
            "user", "pwd", List.of(() -> "member:view", () -> "user:manage"));
    when(identityResolver.resolveIdentity(auth)).thenReturn("user@example.com");

    var resp = currentUserController.currentUser(auth);
    assertThat(resp.username()).isEqualTo("user@example.com");
    assertThat(resp.permissions()).containsExactly("member:view", "user:manage");
  }
}
