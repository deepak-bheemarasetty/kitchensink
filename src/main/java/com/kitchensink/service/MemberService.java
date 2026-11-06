package com.kitchensink.service;

import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import java.time.Instant;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Applies member normalization and persists directory records. */
@Service
public class MemberService {
  private static final Pattern NAME = Pattern.compile("(?U)^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9_+\\-]+(?:\\.[A-Za-z0-9_+\\-]+)*@(?=.{1,255}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$");
  private static final Pattern PHONE = Pattern.compile("\\+[1-9][0-9]{7,14}");
  private final MemberRepository memberRepository;

  public MemberService(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  public Member register(Member member) {
    member.setFirstName(normalizeName(member.getFirstName(), true));
    member.setLastName(normalizeName(member.getLastName(), false));
    if (member.getLastName() == null) member.setLastName("");
    if (member.getPhone() == null || member.getPhone().isBlank()) {
      throw validationError("Phone is required");
    }
    String phone = member.getPhone().trim().replaceAll("[\\s()-]", "");
    if (!PHONE.matcher(phone).matches()) {
      throw validationError("Phone must be in E.164 format");
    }
    member.setPhone(phone);
    member.setEmail(normalizeEmail(member.getEmail()));
    member.setEmailLower(member.getEmail().toLowerCase(Locale.ROOT));
    member.setFullNameLower((member.getFirstName() + " " + member.getLastName()).toLowerCase(Locale.ROOT));
    member.setMemberId("M-" + Instant.now().getEpochSecond() + "-" + new ObjectId().toHexString().substring(0, 6).toUpperCase(Locale.ROOT));
    member.setStatus("active");
    member.setCreatedAt(Instant.now());
    member.setUpdatedAt(member.getCreatedAt());
    member.setVersion(1);
    member.setCreatedBy(new ObjectId());
    member.setUpdatedBy(member.getCreatedBy());
    try {
      return memberRepository.save(member);
    } catch (DuplicateKeyException exception) {
      throw new DuplicateEmailException("Email already registered: " + member.getEmail());
    }
  }

  private String normalizeName(String value, boolean required) {
    if (value == null) {
      if (required) throw validationError("First name is required");
      return null;
    }
    String normalized = Normalizer.normalize(value.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    if (normalized.codePoints().anyMatch(Character::isISOControl)) throw validationError("Name contains a control character");
    if (normalized.isEmpty() && !required) return null;
    if (normalized.isEmpty() || normalized.length() > 50 || !NAME.matcher(normalized).matches()
        || normalized.codePoints().noneMatch(Character::isLetter)) {
      throw validationError(required ? "First name is invalid" : "Last name is invalid");
    }
    return normalized;
  }

  private String normalizeEmail(String value) {
    if (value == null) throw validationError("Email is required");
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    if (normalized.codePoints().anyMatch(Character::isISOControl) || normalized.length() < 3
        || normalized.length() > 254 || !EMAIL.matcher(normalized).matches()
        || normalized.substring(0, normalized.indexOf('@')).length() > 64) {
      throw validationError("Email format is invalid");
    }
    return normalized;
  }

  private ResponseStatusException validationError(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  /** Finds a member by email, reporting inactive records distinctly from missing records. */
  public Member findByEmail(String email) {
    Member member =
        memberRepository
            .findByEmailLowerAndDeletedAtIsNull(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    if (!"active".equalsIgnoreCase(member.getStatus()) || member.getDeletedAt() != null) {
      throw new ResponseStatusException(HttpStatus.GONE, "Member is no longer active");
    }
    return member;
  }
}
