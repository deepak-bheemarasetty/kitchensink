package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.MemberPatchRequest;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for MemberService business logic.
 *
 * @author Deepak Bheemarasetty
 */
class MemberServiceTest {

  private MemberRepository memberRepository;
  private MongoTemplate mongoTemplate;
  private IdentityResolver identityResolver;
  private RolePermissionService rolePermissionService;
  private MemberService memberService;

  @BeforeEach
  void setUp() {
    memberRepository = mock(MemberRepository.class);
    mongoTemplate = mock(MongoTemplate.class);
    identityResolver = mock(IdentityResolver.class);
    rolePermissionService = mock(RolePermissionService.class);
    when(rolePermissionService.user(any()))
        .thenReturn(
            new RolePermissionService.UserPermissions(
                true,
                Set.of(
                    "member:view",
                    "member:create",
                    "member:update",
                    "member:delete",
                    "user:manage",
                    "role:manage")));
    when(identityResolver.resolveIdentity(any())).thenReturn("admin");
    memberService =
        new MemberService(memberRepository, mongoTemplate, identityResolver, rolePermissionService);

    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(
            "admin",
            "pwd",
            List.of(
                () -> "member:view",
                () -> "member:create",
                () -> "member:update",
                () -> "member:delete"));
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.requireActorId(any())).thenReturn(new ObjectId());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void registerSuccess() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setLastName("Doe");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");
    member.setCountryCode("1");

    when(mongoTemplate.insert(any(Member.class)))
        .thenAnswer(
            inv -> {
              Member m = inv.getArgument(0);
              m.setId(new ObjectId());
              return m;
            });

    Member saved = memberService.register(member, "key-123");
    assertThat(saved).isNotNull();
    assertThat(saved.getMemberId()).startsWith("M-");
    assertThat(saved.getStatus()).isEqualTo("active");
  }

  @Test
  void registerDuplicateKeyThrowsDuplicateEmailException() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setLastName("Doe");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");

    when(mongoTemplate.insert(any(Member.class))).thenThrow(new DuplicateKeyException("dup"));
    assertThatThrownBy(() -> memberService.register(member, null))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  void registerIdempotentReplay() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");

    ObjectId existingId = new ObjectId();
    Document prior = new Document("memberId", existingId);
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("idempotency_keys")))
        .thenReturn(prior);

    Member existingMember = Member.create();
    existingMember.setId(existingId);
    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(existingMember);

    Member result = memberService.register(member, "key-prior");
    assertThat(result.getId()).isEqualTo(existingId);
  }

  @Test
  void findByIdAndMemberId() {
    ObjectId id = new ObjectId();
    Member member = Member.create();
    member.setId(id);
    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(member);

    assertThat(memberService.findById(id)).isEqualTo(member);
    assertThat(memberService.findByMemberId("M-1")).isEqualTo(member);
  }

  @Test
  void findByEmailSuccessAndGone() {
    Member member = Member.create();
    member.setEmail("active@example.com");
    member.setStatus("active");
    when(memberRepository.findByEmailLowerAndDeletedAtIsNull("active@example.com"))
        .thenReturn(Optional.of(member));

    assertThat(memberService.findByEmailActive("active@example.com")).isEqualTo(member);

    member.setStatus("inactive");
    assertThatThrownBy(() -> memberService.findByEmail("active@example.com"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void updateMember() {
    ObjectId id = new ObjectId();
    Member current = Member.create();
    current.setId(id);
    current.setFirstName("John");
    current.setLastName("Doe");
    current.setEmail("john@example.com");
    current.setPhone("2125551212");
    current.setStatus("active");
    current.setVersion(1);

    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(current);
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class))).thenReturn(current);

    MemberPatchRequest patch = new MemberPatchRequest();
    patch.setFirstName("Johnny");
    patch.setVersion(1);

    Member updated = memberService.update(id, patch);
    assertThat(updated).isNotNull();
  }

  @Test
  void softDeleteAndRestore() {
    ObjectId id = new ObjectId();
    Member member = Member.create();
    member.setId(id);
    member.setDeletedAt(java.time.Instant.now());
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class))).thenReturn(member);

    memberService.softDelete(id);
    assertThat(memberService.restore(id)).isEqualTo(member);
  }

  @Test
  void searchMembers() {
    Member member = Member.create();
    member.setId(new ObjectId());
    member.setFullNameLower("john doe");
    member.setEmailLower("john@example.com");

    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of(member));

    var page = memberService.search("john", "active", "name", 10, null);
    assertThat(page.data()).hasSize(1);
    assertThat(page.hasMore()).isFalse();
  }

  @Test
  void permissionDeniedWithoutAuthority() {
    TestingAuthenticationToken token = new TestingAuthenticationToken("viewer", "pwd", List.of());
    SecurityContextHolder.getContext().setAuthentication(token);
    when(identityResolver.resolveIdentity(token)).thenReturn("viewer");
    when(rolePermissionService.user("viewer"))
        .thenReturn(
            new RolePermissionService.UserPermissions(false, java.util.Collections.emptySet()));

    Member member = Member.create();
    assertThatThrownBy(() -> memberService.register(member))
        .isInstanceOf(AccessDeniedException.class);
  }
}
