package com.kitchensink.service;

import com.kitchensink.dto.MemberPatchRequest;
import com.kitchensink.model.AuditLog;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Applies member normalization and persists directory records. */
@Service
public class MemberService {
  private final MemberRepository memberRepository;
  private final MongoTemplate mongoTemplate;

  public MemberService(MemberRepository memberRepository, MongoTemplate mongoTemplate) {
    this.memberRepository = memberRepository;
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Registers and persists a new member record.
   *
   * @param member member entity to persist
   * @return registered member
   */
  @Transactional
  public Member register(Member member) {
    requirePermission("member:create");
    member.setFirstName(Validator.firstName(member.getFirstName()));
    member.setLastName(Validator.lastName(member.getLastName()));
    if (member.getLastName() == null) {
      member.setLastName("");
    }
    member.setPhone(Validator.phone(member.getPhone()));
    member.setEmail(Validator.email(member.getEmail()));
    member.setEmailLower(member.getEmail().toLowerCase(Locale.ROOT));
    member.setFullNameLower(
        (member.getFirstName() + " " + member.getLastName()).toLowerCase(Locale.ROOT));
    member.setMemberId(
        "M-"
            + Instant.now().getEpochSecond()
            + "-"
            + new ObjectId().toHexString().substring(0, 6).toUpperCase(Locale.ROOT));
    member.setStatus("active");
    member.setCreatedAt(Instant.now());
    member.setUpdatedAt(member.getCreatedAt());
    member.setVersion(1);
    ObjectId actorId = currentActorId();
    member.setCreatedBy(actorId);
    member.setUpdatedBy(member.getCreatedBy());
    member.validate();
    try {
      Member saved = mongoTemplate.insert(member);
      audit(
          "member.create",
          saved,
          Map.of(
              "after",
              Map.of(
                  "firstName",
                  saved.getFirstName(),
                  "lastName",
                  saved.getLastName(),
                  "email",
                  saved.getEmail(),
                  "phone",
                  saved.getPhone(),
                  "status",
                  saved.getStatus())));
      return saved;
    } catch (DuplicateKeyException exception) {
      throw new DuplicateEmailException("Email already registered: " + member.getEmail());
    }
  }

  /**
   * Finds an active member by object id.
   *
   * @param id member object id
   * @return matching member
   */
  public Member findById(ObjectId id) {
    requirePermission("member:view");
    return mongoTemplate.findOne(
        Query.query(Criteria.where("_id").is(id).and("deletedAt").is(null)), Member.class);
  }

  /**
   * Finds an active member by business member id.
   *
   * @param memberId business member id
   * @return matching member
   */
  public Member findByMemberId(String memberId) {
    requirePermission("member:view");
    return mongoTemplate.findOne(
        Query.query(Criteria.where("memberId").is(memberId).and("deletedAt").is(null)),
        Member.class);
  }

  public Member findByEmailActive(String email) {
    return findByEmail(email);
  }

  /**
   * Searches members using pagination and filtering.
   *
   * @param q search query string
   * @param status status filter
   * @param sortField field to sort by
   * @param limit maximum items to return
   * @param cursor pagination cursor
   * @return paginated member results
   */
  public MemberPage search(String q, String status, String sortField, int limit, String cursor) {
    requirePermission("member:view");
    q = Validator.searchQuery(q);
    sortField = Validator.sortField(sortField);
    String sortKey = sortField.equals("name") ? "fullNameLower" : "emailLower";
    List<Criteria> filters = new ArrayList<>();
    filters.add(Criteria.where("deletedAt").is(null));
    cursor = Validator.cursor(cursor);
    if (status != null && !status.isBlank()) {
      status = Validator.status(status);
      filters.add(Criteria.where("status").is(status));
    }
    if (q != null && !q.isBlank()) {
      String value = q.trim().toLowerCase(Locale.ROOT);
      String escaped = java.util.regex.Pattern.quote(value);
      filters.add(
          new Criteria()
              .orOperator(
                  Criteria.where("fullNameLower").regex("^" + escaped),
                  Criteria.where("emailLower").regex("^" + escaped)));
    }
    if (cursor != null && !cursor.isBlank()) {
      String[] parts;
      try {
        parts =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", 2);
      } catch (IllegalArgumentException exception) {
        throw new com.kitchensink.validation.ValidationException("cursor", "cursor is invalid");
      }
      if (parts.length != 2 || !ObjectId.isValid(parts[1])) {
        throw new com.kitchensink.validation.ValidationException("cursor", "cursor is invalid");
      }
      ObjectId cursorId = new ObjectId(parts[1]);
      filters.add(
          new Criteria()
              .orOperator(
                  Criteria.where(sortKey).gt(parts[0]),
                  new Criteria()
                      .andOperator(
                          Criteria.where(sortKey).is(parts[0]),
                          Criteria.where("_id").gt(cursorId))));
    }
    final int pageSize = Validator.pageLimit(limit);
    final Query query = new Query();
    query.addCriteria(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
    query.with(Sort.by(Sort.Order.asc(sortKey), Sort.Order.asc("_id"))).limit(pageSize + 1);
    List<Member> found = new ArrayList<>(mongoTemplate.find(query, Member.class));
    boolean hasMore = found.size() > pageSize;
    if (hasMore) {
      found.remove(pageSize);
    }
    String nextCursor = null;
    if (hasMore && !found.isEmpty()) {
      Member last = found.getLast();
      String value = sortField.equals("name") ? last.getFullNameLower() : last.getEmailLower();
      nextCursor =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString((value + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
    }
    return new MemberPage(found, pageSize, nextCursor, hasMore);
  }

  /**
   * Updates an existing member with optimistic locking.
   *
   * @param id member object id
   * @param patch partial update data
   * @return updated member
   */
  @Transactional
  public Member update(ObjectId id, MemberPatchRequest patch) {
    requirePermission("member:update");
    patch.validate();
    Member existing =
        mongoTemplate.findOne(
            Query.query(
                Criteria.where("_id")
                    .is(id)
                    .and("version")
                    .is(patch.getVersion())
                    .and("deletedAt")
                    .is(null)),
            Member.class);
    if (existing == null) {
      Member latest = mongoTemplate.findById(id, Member.class);
      if (latest == null || latest.getDeletedAt() != null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
      }
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Member was changed; reload the current record");
    }
    String firstName =
        patch.getFirstName() == null
            ? existing.getFirstName()
            : Validator.firstName(patch.getFirstName());
    String lastName =
        patch.getLastName() == null
            ? existing.getLastName()
            : Validator.lastName(patch.getLastName());
    if (lastName == null) {
      lastName = "";
    }
    Query query =
        Query.query(
            Criteria.where("_id")
                .is(id)
                .and("version")
                .is(patch.getVersion())
                .and("deletedAt")
                .is(null));
    Update update =
        new Update()
            .set("updatedAt", Date.from(Instant.now()))
            .set("updatedBy", currentActorId())
            .set("firstName", firstName)
            .set("lastName", lastName)
            .set("fullNameLower", (firstName + " " + lastName).trim().toLowerCase(Locale.ROOT))
            .inc("version", 1);
    if (patch.getEmail() != null) {
      String email = Validator.email(patch.getEmail());
      update.set("email", email).set("emailLower", email.toLowerCase(Locale.ROOT));
    }
    if (patch.getPhone() != null) {
      update.set("phone", Validator.phone(patch.getPhone()));
    }
    if (patch.getStatus() != null) {
      update.set("status", patch.getStatus());
    }
    Member current;
    try {
      current =
          mongoTemplate.findAndModify(
              query, update, FindAndModifyOptions.options().returnNew(true), Member.class);
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A member with this email already exists");
    }
    if (current != null) {
      audit(
          "member.update",
          current,
          Map.of("version", patch.getVersion(), "updatedVersion", current.getVersion()));
      return current;
    }
    Member latest = mongoTemplate.findById(id, Member.class);
    if (latest == null || latest.getDeletedAt() != null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    }
    throw new ResponseStatusException(
        HttpStatus.CONFLICT, "Member was changed; reload the current record");
  }

  /**
   * Performs a soft delete on a member record.
   *
   * @param id member object id
   */
  @Transactional
  public void softDelete(ObjectId id) {
    requirePermission("member:delete");
    Update update =
        new Update()
            .set("deletedAt", Date.from(Instant.now()))
            .set("deletedBy", currentActorId())
            .set("updatedAt", Date.from(Instant.now()))
            .set("updatedBy", currentActorId())
            .inc("version", 1);
    Member member =
        mongoTemplate.findAndModify(
            Query.query(Criteria.where("_id").is(id).and("deletedAt").is(null)),
            update,
            FindAndModifyOptions.options().returnNew(true),
            Member.class);
    if (member == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    }
    audit("member.delete", member, Map.of("deletedAt", member.getDeletedAt()));
  }

  /**
   * Restores a soft-deleted member record.
   *
   * @param id member object id
   * @return restored member
   */
  @Transactional
  public Member restore(ObjectId id) {
    requirePermission("member:delete");
    Update update =
        new Update()
            .set("deletedAt", null)
            .set("deletedBy", null)
            .set("updatedAt", Date.from(Instant.now()))
            .set("updatedBy", currentActorId())
            .inc("version", 1);
    try {
      Member restored =
          mongoTemplate.findAndModify(
              Query.query(Criteria.where("_id").is(id).and("deletedAt").ne(null)),
              update,
              FindAndModifyOptions.options().returnNew(true),
              Member.class);
      if (restored == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deleted member not found");
      }
      audit("member.restore", restored, Map.of("deletedAt", "cleared"));
      return restored;
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A member with this email or member ID already exists");
    }
  }

  private void audit(String action, Member member, Map<String, Object> changes) {
    AuditLog log = new AuditLog();
    log.setAt(Instant.now());
    log.setActorId(currentActorId());
    log.setAction(action);
    log.setEntityType("member");
    log.setEntityId(member.getId());
    log.setChanges(changes);
    log.setRequestId(UUID.randomUUID().toString());
    log.validateForInsert();
    mongoTemplate.insert(log);
  }

  private ObjectId currentActorId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      throw new AccessDeniedException("Authentication required");
    }
    String username = authentication.getName();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      String email = jwt.getToken().getClaimAsString("email");
      if (email != null) {
        username = email;
      }
    }
    org.bson.Document user =
        mongoTemplate.findOne(
            Query.query(Criteria.where("usernameLower").is(username.toLowerCase(Locale.ROOT))),
            org.bson.Document.class,
            "users");
    if (user == null) {
      throw new AccessDeniedException("Authenticated user is not registered in the application");
    }
    return user.getObjectId("_id");
  }

  /** Paginated member results and cursor information. */
  public record MemberPage(List<Member> data, int limit, String nextCursor, boolean hasMore) {}

  /** Finds a member by email, reporting inactive records distinctly from missing records. */
  public Member findByEmail(String email) {
    requirePermission("member:view");
    String normalizedEmail = Validator.email(email);
    Member member =
        memberRepository
            .findByEmailLowerAndDeletedAtIsNull(normalizedEmail.toLowerCase(Locale.ROOT))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    if (!"active".equalsIgnoreCase(member.getStatus()) || member.getDeletedAt() != null) {
      throw new ResponseStatusException(HttpStatus.GONE, "Member is no longer active");
    }
    return member;
  }

  private void requirePermission(String permission) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication.getAuthorities().stream()
            .noneMatch(authority -> authority.getAuthority().equals(permission))) {
      throw new AccessDeniedException("You do not have permission");
    }
  }
}
