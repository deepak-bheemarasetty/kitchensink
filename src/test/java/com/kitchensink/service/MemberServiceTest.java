package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.MemberPatchRequest;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
  void registerRemovesReservedKeyOnDuplicateAndHandlesOptionalName() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");
    when(mongoTemplate.insert(any(Member.class))).thenThrow(new DuplicateKeyException("duplicate"));

    assertThatThrownBy(() -> memberService.register(member, "reserved-key"))
        .isInstanceOf(DuplicateEmailException.class);
    assertThat(member.getLastName()).isEmpty();
    verify(mongoTemplate).remove(any(Query.class), eq("idempotency_keys"));
  }

  @Test
  void registerUsesRequestMdcAndOmitsInvalidClientIp() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setEmail("john@example.com");
    member.setPhone("2125551212");
    when(mongoTemplate.insert(any(Member.class)))
        .thenAnswer(
            invocation -> {
              Member inserted = invocation.getArgument(0);
              inserted.setId(new ObjectId());
              return inserted;
            });
    org.mockito.ArgumentCaptor<com.kitchensink.model.AuditLog> auditCaptor =
        org.mockito.ArgumentCaptor.forClass(com.kitchensink.model.AuditLog.class);
    try {
      MDC.put("requestId", "request-from-mdc");
      MDC.put("clientIp", "203.0.113.1");
      memberService.register(member);
    } finally {
      MDC.remove("requestId");
      MDC.remove("clientIp");
    }

    verify(mongoTemplate).insert(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getRequestId()).isEqualTo("request-from-mdc");
    assertThat(auditCaptor.getValue().getIp()).isEqualTo("203.0.113.1");
  }

  @Test
  void registerIdempotentReplay() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");

    ObjectId existingId = new ObjectId();
    Document prior = new Document("memberId", existingId);
    when(mongoTemplate.insert(any(Document.class), eq("idempotency_keys")))
        .thenThrow(new DuplicateKeyException("key exists"));
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("idempotency_keys")))
        .thenReturn(prior);

    Member existingMember = Member.create();
    existingMember.setId(existingId);
    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(existingMember);

    Member result = memberService.register(member, "key-prior");
    assertThat(result.getId()).isEqualTo(existingId);
  }

  @Test
  void registerIdempotencyKeyConcurrentProcessingThrowsConflict() {
    Member member = Member.create();
    member.setFirstName("John");
    member.setEmail("john.doe@example.com");
    member.setPhone("2125551212");

    when(mongoTemplate.insert(any(Document.class), eq("idempotency_keys")))
        .thenThrow(new DuplicateKeyException("key exists"));
    Document priorProcessing = new Document("memberId", null);
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("idempotency_keys")))
        .thenReturn(priorProcessing);

    assertThatThrownBy(() -> memberService.register(member, "key-processing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Request with this key is still processing");
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
  void findByEmailDistinguishesMissingAndDeletedMembers() {
    when(memberRepository.findByEmailLowerAndDeletedAtIsNull("missing@example.com"))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> memberService.findByEmail("missing@example.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member not found");

    Member deleted = Member.create();
    deleted.setEmail("deleted@example.com");
    deleted.setStatus("active");
    deleted.setDeletedAt(java.time.Instant.now());
    when(memberRepository.findByEmailLowerAndDeletedAtIsNull("deleted@example.com"))
        .thenReturn(Optional.of(deleted));
    assertThatThrownBy(() -> memberService.findByEmail("deleted@example.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member is no longer active");
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
  void updateMapsMissingAndStaleRecordsToNotFoundAndConflict() {
    ObjectId id = new ObjectId();
    MemberPatchRequest patch = new MemberPatchRequest();
    patch.setVersion(1);
    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(null);
    when(mongoTemplate.findById(id, Member.class)).thenReturn(null);
    assertThatThrownBy(() -> memberService.update(id, patch))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member not found");

    Member latest = Member.create();
    latest.setId(id);
    when(mongoTemplate.findById(id, Member.class)).thenReturn(latest);
    assertThatThrownBy(() -> memberService.update(id, patch))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member was changed");

    Member deleted = Member.create();
    deleted.setId(id);
    deleted.setDeletedAt(java.time.Instant.now());
    when(mongoTemplate.findById(id, Member.class)).thenReturn(deleted);
    assertThatThrownBy(() -> memberService.update(id, patch))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member not found");
  }

  @Test
  void updateHandlesDuplicateEmailAndLostRace() {
    ObjectId id = new ObjectId();
    Member existing = Member.create();
    existing.setId(id);
    existing.setFirstName("John");
    existing.setLastName("Doe");
    existing.setEmail("john@example.com");
    existing.setPhone("2125551212");
    existing.setCountryCode("1");
    existing.setStatus("active");
    existing.setVersion(1);

    MemberPatchRequest patch = new MemberPatchRequest();
    patch.setVersion(1);
    patch.setEmail("new@example.com");
    patch.setPhone("2125551213");
    patch.setCountryCode("44");
    patch.setStatus("inactive");
    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(existing);
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class)))
        .thenThrow(new DuplicateKeyException("duplicate"));
    assertThatThrownBy(() -> memberService.update(id, patch))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("A member with this email already exists");

    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class))).thenReturn(null);
    when(mongoTemplate.findById(id, Member.class)).thenReturn(existing);
    MemberPatchRequest lostRace = new MemberPatchRequest();
    lostRace.setVersion(1);
    assertThatThrownBy(() -> memberService.update(id, lostRace))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member was changed");
  }

  @Test
  void updateNormalizesOptionalFieldsAndAuditsSuccess() {
    ObjectId id = new ObjectId();
    Member existing = Member.create();
    existing.setId(id);
    existing.setFirstName("John");
    existing.setLastName("Doe");
    existing.setEmail("john@example.com");
    existing.setPhone("2125551212");
    existing.setCountryCode("1");
    existing.setStatus("active");
    existing.setVersion(1);
    when(mongoTemplate.findOne(any(Query.class), eq(Member.class))).thenReturn(existing);
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class))).thenReturn(existing);

    MemberPatchRequest patch = new MemberPatchRequest();
    patch.setVersion(1);
    patch.setLastName("  ");
    patch.setEmail(" New@example.com ");
    patch.setPhone("1 (212) 555-1213");
    patch.setCountryCode("44");
    patch.setStatus("inactive");

    assertThat(memberService.update(id, patch)).isSameAs(existing);
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
  void softDeleteAndRestoreReportMissingRecords() {
    ObjectId id = new ObjectId();
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class))).thenReturn(null);
    assertThatThrownBy(() -> memberService.softDelete(id))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Member not found");
    assertThatThrownBy(() -> memberService.restore(id))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Deleted member not found");
  }

  @Test
  void restoreMapsDuplicateKeyToConflict() {
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(Member.class)))
        .thenThrow(new DuplicateKeyException("duplicate"));
    assertThatThrownBy(() -> memberService.restore(new ObjectId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("A member with this email or member ID already exists");
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
  void searchBuildsNextCursorAndRejectsMalformedCursor() {
    ObjectId firstId = new ObjectId();
    Member first = Member.create();
    first.setId(firstId);
    first.setFullNameLower("alice");
    first.setEmailLower("alice@example.com");
    Member second = Member.create();
    second.setId(new ObjectId());
    second.setFullNameLower("bob");
    second.setEmailLower("bob@example.com");
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of(first, second));

    var page = memberService.search(null, null, "email", 1, null);
    assertThat(page.data()).containsExactly(first);
    assertThat(page.hasMore()).isTrue();
    assertThat(new String(Base64.getUrlDecoder().decode(page.nextCursor()), StandardCharsets.UTF_8))
        .isEqualTo("alice@example.com|" + firstId);

    String validCursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(("alice|" + firstId).getBytes(StandardCharsets.UTF_8));
    assertThat(memberService.search(" ", " ", "name", 10, validCursor).hasMore()).isFalse();
    assertThatThrownBy(() -> memberService.search(null, null, "name", 10, "bad"))
        .isInstanceOf(com.kitchensink.validation.ValidationException.class);
    assertThatThrownBy(() -> memberService.search(null, null, "name", 10, "%%%"))
        .isInstanceOf(com.kitchensink.validation.ValidationException.class);
    String invalidObjectId =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("alice|not-an-object-id".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> memberService.search(null, null, "name", 10, invalidObjectId))
        .isInstanceOf(com.kitchensink.validation.ValidationException.class);
    String wrongParts =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("one-part".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> memberService.search(null, null, "name", 10, wrongParts))
        .isInstanceOf(com.kitchensink.validation.ValidationException.class);
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

  @Test
  void unauthenticatedAndMissingPermissionRequestsAreDenied() {
    Member member = Member.create();
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> memberService.register(member))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Authentication required");

    TestingAuthenticationToken auth = new TestingAuthenticationToken("viewer", "pwd", List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    when(identityResolver.resolveIdentity(auth)).thenReturn("viewer");
    when(rolePermissionService.user("viewer"))
        .thenReturn(new RolePermissionService.UserPermissions(true, Set.of("member:view")));
    assertThatThrownBy(() -> memberService.register(member))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("You do not have permission");
  }
}
