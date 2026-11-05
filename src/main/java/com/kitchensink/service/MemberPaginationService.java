package com.kitchensink.service;

import com.kitchensink.model.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Provides unique-email-index-backed keyset pagination for the member directory. */
@Service
public class MemberPaginationService {
  public static final int PAGE_SIZE = 20;

  private final MongoTemplate mongoTemplate;

  public MemberPaginationService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Retrieves a page using the unique email cursor without offset skips or a count query.
   */
  public MemberCursorPage getPage(String afterEmail, String beforeEmail) {
    return getPage(afterEmail, beforeEmail, null, false);
  }

  private MemberCursorPage getPage(
      String afterEmail,
      String beforeEmail,
      String emailPrefix,
      boolean summaryOnly) {
    validateCursors(afterEmail, beforeEmail);
    boolean previousRequest = beforeEmail != null;
    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();
    if (previousRequest) {
      criteria.add(cursorCriteria(beforeEmail, false));
      query.with(Sort.by(Sort.Order.desc("email")));
    } else if (afterEmail != null) {
      criteria.add(cursorCriteria(afterEmail, true));
      query.with(Sort.by(Sort.Order.asc("email")));
    } else {
      query.with(Sort.by(Sort.Order.asc("email")));
    }
    if (emailPrefix != null && !emailPrefix.isBlank()) {
      String normalizedPrefix = emailPrefix.trim();
      if (normalizedPrefix.length() < 4) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Email filter must contain at least 4 characters");
      }
      criteria.add(
          Criteria.where("email").regex(emailPrefixPattern(normalizedPrefix), "i"));
    }
    if (criteria.size() == 1) {
      query.addCriteria(criteria.getFirst());
    } else if (criteria.size() > 1) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
    }
    query.limit(PAGE_SIZE + 1);
    if (summaryOnly) {
      query.fields().include("id").include("email").include("name").include("phoneNumber");
    }

    List<Member> results = new ArrayList<>(mongoTemplate.find(query, Member.class));
    boolean hasMoreInRequestedDirection = results.size() > PAGE_SIZE;
    if (hasMoreInRequestedDirection) {
      results.remove(PAGE_SIZE);
    }
    if (previousRequest) {
      Collections.reverse(results);
    }

    boolean hasPrevious =
        !results.isEmpty() && (previousRequest ? hasMoreInRequestedDirection : afterEmail != null);
    boolean hasNext = !results.isEmpty() && (previousRequest || hasMoreInRequestedDirection);
    return new MemberCursorPage(results, hasPrevious, hasNext);
  }

  /** Retrieves the member fields displayed by the browser list. */
  public MemberCursorPage getSummaryPage(String afterEmail, String beforeEmail) {
    return getSummaryPage(afterEmail, beforeEmail, null);
  }

  /** Retrieves list fields for the browser, optionally matching an email prefix. */
  public MemberCursorPage getSummaryPage(
      String afterEmail, String beforeEmail, String emailPrefix) {
    return getPage(afterEmail, beforeEmail, emailPrefix, true);
  }

  private Criteria cursorCriteria(String email, boolean forward) {
    Criteria emailAfterOrBefore = Criteria.where("email");
    if (forward) {
      emailAfterOrBefore.gt(email);
    } else {
      emailAfterOrBefore.lt(email);
    }
    return emailAfterOrBefore;
  }

  private String emailPrefixPattern(String prefix) {
    StringBuilder pattern = new StringBuilder("^");
    for (char character : prefix.toCharArray()) {
      if ("\\.^$|?*+()[]{}".indexOf(character) >= 0) {
        pattern.append('\\');
      }
      pattern.append(character);
    }
    return pattern.toString();
  }

  private void validateCursors(String afterEmail, String beforeEmail) {
    if ((afterEmail != null && afterEmail.isBlank())
        || (beforeEmail != null && beforeEmail.isBlank())
        || (afterEmail != null && beforeEmail != null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide one email cursor at a time");
    }
  }

  /** One cursor page without total-count metadata. */
  public record MemberCursorPage(List<Member> members, boolean hasPrevious, boolean hasNext) {
    public String previousEmail() {
      return members.isEmpty() ? null : members.getFirst().getEmail();
    }

    public String nextEmail() {
      return members.isEmpty() ? null : members.getLast().getEmail();
    }
  }
}
