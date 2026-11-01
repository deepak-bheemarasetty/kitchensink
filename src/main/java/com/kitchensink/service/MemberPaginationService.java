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

/** Provides index-backed keyset pagination for the member directory. */
@Service
public class MemberPaginationService {
  public static final int PAGE_SIZE = 10;

  /** Retrieves a numbered page and total count for the member directory. */
  public NumberedMemberPage getNumberedPage(int requestedPage) {
    return getNumberedPage(requestedPage, null);
  }

  /** Retrieves a numbered page filtered by an email fragment. */
  public NumberedMemberPage getNumberedPage(int requestedPage, String emailFilter) {
    int page = Math.max(0, requestedPage);
    Query countQuery =
        emailFilter == null || emailFilter.isBlank()
            ? new Query()
            : Query.query(
                Criteria.where("email")
                    .regex(java.util.regex.Pattern.quote(emailFilter), "i"));
    long total = mongoTemplate.count(countQuery, Member.class);
    int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    page = Math.min(page, totalPages - 1);
    Query query = new Query();
    if (emailFilter != null && !emailFilter.isBlank()) {
      query.addCriteria(
          Criteria.where("email").regex(java.util.regex.Pattern.quote(emailFilter), "i"));
    }
    query.with(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("email")));
    query.skip((long) page * PAGE_SIZE);
    query.limit(PAGE_SIZE);
    query.fields().include("id").include("name").include("email").include("phoneNumber");
    return new NumberedMemberPage(mongoTemplate.find(query, Member.class), page, total, totalPages);
  }

  private final MongoTemplate mongoTemplate;

  public MemberPaginationService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Retrieves a page using a stable name/email cursor without offset skips or a count query.
   */
  public MemberCursorPage getPage(
      String afterName, String afterEmail, String beforeName, String beforeEmail) {
    return getPage(afterName, afterEmail, beforeName, beforeEmail, false);
  }

  private MemberCursorPage getPage(
      String afterName,
      String afterEmail,
      String beforeName,
      String beforeEmail,
      boolean summaryOnly) {
    validateCursors(afterName, afterEmail, beforeName, beforeEmail);
    boolean previousRequest = beforeName != null;
    Query query = new Query();
    if (previousRequest) {
      query.addCriteria(cursorCriteria(beforeName, beforeEmail, false));
      query.with(Sort.by(Sort.Order.desc("name"), Sort.Order.desc("email")));
    } else if (afterName != null) {
      query.addCriteria(cursorCriteria(afterName, afterEmail, true));
      query.with(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("email")));
    } else {
      query.with(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("email")));
    }
    query.limit(PAGE_SIZE + 1);
    if (summaryOnly) {
      query.fields().include("id").include("name").include("email");
    }

    List<Member> results = new ArrayList<>(mongoTemplate.find(query, Member.class));
    boolean hasMoreInRequestedDirection = results.size() > PAGE_SIZE;
    if (hasMoreInRequestedDirection) {
      results.remove(PAGE_SIZE);
    }
    if (previousRequest) {
      Collections.reverse(results);
    }

    boolean hasPrevious = previousRequest ? hasMoreInRequestedDirection : afterName != null;
    boolean hasNext = previousRequest || hasMoreInRequestedDirection;
    return new MemberCursorPage(results, hasPrevious, hasNext);
  }

  /** Retrieves only list fields for the browser without loading phone numbers. */
  public MemberCursorPage getSummaryPage(
      String afterName, String afterEmail, String beforeName, String beforeEmail) {
    return getPage(afterName, afterEmail, beforeName, beforeEmail, true);
  }

  private Criteria cursorCriteria(String name, String email, boolean forward) {
    Criteria nameAfterOrBefore = Criteria.where("name");
    Criteria emailAfterOrBefore = Criteria.where("email");
    if (forward) {
      nameAfterOrBefore.gt(name);
      emailAfterOrBefore.gt(email);
    } else {
      nameAfterOrBefore.lt(name);
      emailAfterOrBefore.lt(email);
    }
    return new Criteria()
        .orOperator(
            nameAfterOrBefore,
            new Criteria()
                .andOperator(
                    Criteria.where("name").is(name),
                    emailAfterOrBefore));
  }

  private void validateCursors(
      String afterName, String afterEmail, String beforeName, String beforeEmail) {
    boolean afterIncomplete = (afterName == null) != (afterEmail == null);
    boolean beforeIncomplete = (beforeName == null) != (beforeEmail == null);
    if (afterIncomplete || beforeIncomplete || (afterName != null && beforeName != null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide one complete pagination cursor at a time");
    }
  }

  /** One cursor page without total-count metadata. */
  public record MemberCursorPage(List<Member> members, boolean hasPrevious, boolean hasNext) {
    public String previousName() {
      return members.isEmpty() ? null : members.getFirst().getName();
    }

    public String previousEmail() {
      return members.isEmpty() ? null : members.getFirst().getEmail();
    }

    public String nextName() {
      return members.isEmpty() ? null : members.getLast().getName();
    }

    public String nextEmail() {
      return members.isEmpty() ? null : members.getLast().getEmail();
    }
  }

  /** Represents a numbered page of members and its pagination metadata. */
  public record NumberedMemberPage(
      List<Member> members, int page, long totalMembers, int totalPages) {
    /** Returns whether an earlier page exists. */
    public boolean hasPrevious() {
      return page > 0;
    }

    /** Returns whether a later page exists. */
    public boolean hasNext() {
      return page < totalPages - 1;
    }
  }
}
