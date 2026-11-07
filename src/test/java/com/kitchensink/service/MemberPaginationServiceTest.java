package com.kitchensink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Member;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

/** Tests email-index-backed member pagination and cursor validation. */
@ExtendWith(MockitoExtension.class)
class MemberPaginationServiceTest {
  @Mock private MongoTemplate mongoTemplate;

  private MemberPaginationService paginationService;

  @BeforeEach
  void setUp() {
    paginationService = new MemberPaginationService(mongoTemplate);
  }

  @Test
  void firstPageFetchesOnlyTwentyOneSortedRowsWithoutSkipOrCount() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(members(21));

    MemberPaginationService.MemberCursorPage page = paginationService.getPage(null, null);

    assertThat(page.members()).hasSize(20);
    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isTrue();
    assertThat(page.previousEmail()).isEqualTo("member00@example.com");
    assertThat(page.nextEmail()).isEqualTo("member19@example.com");

    Query query = captureQuery();
    assertThat(query.getLimit()).isEqualTo(21);
    assertThat(query.getSkip()).isZero();
    assertThat(query.getSortObject()).isEqualTo(new Document("email", 1));
    assertThat(query.getQueryObject()).isEmpty();
  }

  @Test
  void summaryPageProjectsOnlyMemberListFields() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of(member(1)));

    MemberPaginationService.MemberCursorPage page = paginationService.getSummaryPage(null, null);

    assertThat(page.members()).hasSize(1);
    Query query = captureQuery();
    assertThat(query.getFieldsObject())
        .containsKeys("id", "name", "email", "phoneNumber", "countryCode");
  }

  @Test
  void nextPageUsesExclusiveEmailCursorAndNoOffsetSkip() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of(member(20)));

    MemberPaginationService.MemberCursorPage page =
        paginationService.getPage("member19@example.com", null);

    assertThat(page.members()).hasSize(1);
    assertThat(page.hasPrevious()).isTrue();
    assertThat(page.hasNext()).isFalse();
    Query query = captureQuery();
    assertThat(query.getSkip()).isZero();
    assertThat(query.getLimit()).isEqualTo(21);
    assertThat(query.getSortObject()).isEqualTo(new Document("email", 1));
    assertThat(query.getQueryObject().toJson()).contains("$gt");
  }

  @Test
  void previousPageFetchesBackwardAndRestoresAscendingOrder() {
    List<Member> descendingMembers = new ArrayList<>(members(21));
    java.util.Collections.reverse(descendingMembers);
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(descendingMembers);

    MemberPaginationService.MemberCursorPage page =
        paginationService.getPage(null, "member21@example.com");

    assertThat(page.members()).hasSize(20);
    assertThat(page.members().getFirst().getEmail()).isEqualTo("member01@example.com");
    assertThat(page.members().getLast().getEmail()).isEqualTo("member20@example.com");
    assertThat(page.hasPrevious()).isTrue();
    assertThat(page.hasNext()).isTrue();
    Query query = captureQuery();
    assertThat(query.getSkip()).isZero();
    assertThat(query.getSortObject()).isEqualTo(new Document("email", -1));
    assertThat(query.getQueryObject().toJson()).contains("$lt");
  }

  @Test
  void previousPageWithoutFurtherResultsHasNoPreviousLink() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class)))
        .thenReturn(List.of(member(2), member(1)));

    MemberPaginationService.MemberCursorPage page =
        paginationService.getPage(null, "member03@example.com");

    assertThat(page.members())
        .extracting(Member::getEmail)
        .containsExactly("member01@example.com", "member02@example.com");
    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isTrue();
  }

  @Test
  void summaryPageCombinesExactCaseInsensitiveFilterWithCursor() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of(member(2)));

    paginationService.getSummaryPage("member01@example.com", null, "MEMBER@example.com");

    Query query = captureQuery();
    String queryJson = query.getQueryObject().toJson();
    assertThat(queryJson).contains("$gt");
    assertThat(queryJson).contains("MEMBER@example");
    assertThat(queryJson).contains("$regularExpression");
    assertThat(queryJson).contains("\"options\": \"i\"");
  }

  @Test
  void whitespaceEmailPrefixIsIgnored() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of());

    paginationService.getSummaryPage(null, null, "   ");

    assertThat(captureQuery().getQueryObject()).isEmpty();
  }

  @Test
  void rejectsEmailPrefixShorterThanFourCharacters() {
    assertThatThrownBy(() -> paginationService.getSummaryPage(null, null, "abc"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void emptyInitialPageHasNoCursorValues() {
    when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of());

    MemberPaginationService.MemberCursorPage page = paginationService.getPage(null, null);

    assertThat(page.previousEmail()).isNull();
    assertThat(page.nextEmail()).isNull();
    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void rejectsBlankCursor() {
    assertThatThrownBy(() -> paginationService.getPage(" ", null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsBlankPreviousCursor() {
    assertThatThrownBy(() -> paginationService.getPage(null, " "))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsTwoCursorsAtOnce() {
    assertThatThrownBy(
            () -> paginationService.getPage("member01@example.com", "member02@example.com"))
        .isInstanceOf(ResponseStatusException.class);
  }

  private Query captureQuery() {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(Member.class));
    return queryCaptor.getValue();
  }

  private static List<Member> members(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(MemberPaginationServiceTest::member)
        .toList();
  }

  private static Member member(int index) {
    Member member = Member.create();
    member.setId((long) index);
    member.setName("Member %02d".formatted(index));
    member.setEmail("member%02d@example.com".formatted(index));
    member.setPhoneNumber("2125551234");
    member.setCountryCode(1);
    return member;
  }
}
