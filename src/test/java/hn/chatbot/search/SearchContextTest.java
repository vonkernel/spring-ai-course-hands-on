package hn.chatbot.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchContextTest {

    @Test
    @DisplayName("원문이 없으면 검색어를 원문으로 보고, 빈 필터는 싣지 않는다")
    void userQuestionFallsBackToText() {
        Query withQuestion = SearchContext.query("coding agent limits", "코딩 에이전트 한계", " ", "NEWS_REPORT");
        Query withoutQuestion = SearchContext.query("coding agent limits", null, null, null);

        assertThat(SearchContext.userQuestion(withQuestion)).isEqualTo("코딩 에이전트 한계");
        assertThat(SearchContext.userQuestion(withoutQuestion)).isEqualTo("coding agent limits");
        assertThat(SearchContext.techField(withQuestion)).isNull();
        assertThat(SearchContext.category(withQuestion)).isEqualTo("NEWS_REPORT");
    }

    @Test
    @DisplayName("계획별 요약과 병합 건수를 Query 의 컨텍스트에 기록한다")
    void records() {
        Query query = SearchContext.query("q", null, null, null);
        SearchContext.record(query, List.of(new PlanSummary(1, "키워드 완전 일치", 2, "keywords=[VMware], suitable")), 7);

        assertThat(SearchContext.planSummaries(query.context())).extracting(PlanSummary::hits).containsExactly(2);
        assertThat(SearchContext.merged(query.context())).isEqualTo(7);
        assertThat(SearchContext.planSummaries(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("후보는 Document 로 바꿨다가 그대로 되읽을 수 있다")
    void candidateRoundTrip() {
        Candidate candidate = new Candidate(42L, "title", "summary", List.of(2, 3));

        Document document = candidate.toDocument();

        assertThat(document.getId()).isEqualTo("42");
        assertThat(Candidate.from(document)).isEqualTo(candidate);
        assertThat(new Candidate(1L, "only title", null, List.of(1)).toDocument().getText()).isEqualTo("only title");
    }
}
