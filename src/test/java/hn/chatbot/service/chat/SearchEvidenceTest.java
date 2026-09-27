package hn.chatbot.service.chat;

import hn.chatbot.ai.RelevantStory;
import hn.chatbot.search.PlanSummary;
import hn.chatbot.search.SearchContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchEvidenceTest {

    private static final List<Document> EVIDENCE = EvidenceDocuments.evidence(
            EvidenceDocumentsTest.CANDIDATES,
            List.of(new RelevantStory(2L, null), new RelevantStory(1L, null)),
            EvidenceDocumentsTest.DETAILS, Map.of());

    @Test
    @DisplayName("계획별 요약과 병합 건수는 검색 컨텍스트에서, 근거는 Document 에서 읽는다")
    void ofQuery() {
        Query query = SearchContext.query("q", null, null, null);
        SearchContext.record(query, List.of(new PlanSummary(3, "원문 청크 벡터 검색", 2, "query=\"q\", suitable, top 5")), 2);

        SearchEvidence e = SearchEvidence.of(query, EVIDENCE);

        assertThat(e.plans()).extracting(PlanSummary::plan).containsExactly(3);
        assertThat(e.merged()).isEqualTo(2);
        assertThat(e.evidence()).extracting(SearchEvidence.Item::storyId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("증강에 쓰는 형식은 도구 결과와 같은 근거 JSON 이다")
    void format() {
        String json = SearchEvidence.format(EVIDENCE);

        assertThat(json).startsWith("{\"evidence\":[");
        assertThat(json).contains("\"rank\":1", "\"storyId\":2", "\"category\":\"OPINION_ESSAY\"");
        assertThat(json.indexOf("\"storyId\":2")).isLessThan(json.indexOf("\"storyId\":1"));
    }
}
