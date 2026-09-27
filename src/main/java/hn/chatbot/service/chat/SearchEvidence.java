package hn.chatbot.service.chat;

import hn.chatbot.search.PlanSummary;
import hn.chatbot.search.SearchContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.util.json.JsonParser;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 검색 한 번의 결과. 화면 사건과 기억 기록의 원본이고, 모델이 답을 쓸 근거다. 완성본이다.
 *
 * - plans: 계획별 건수와 실행 조건. 모델이 답변에서 검색 조건을 설명할 때 쓴다
 * - evidence: 적합성 판단이 고른 순서 그대로의 근거. rank 가 근거 카드 번호다
 *
 * of 가 근거 Document 와 검색 컨텍스트를 합친다. 계획별 요약과 병합 건수는
 * SearchService 가 컨텍스트에 기록해 둔 값이다.
 *
 * 모델에게 가는 모양은 ChatService 두 단계가 같다. 2단계에서는 searchIssues 가 이 레코드를 반환하고
 * Spring AI 가 JSON 으로 바꿔 모델에 넘긴다. 1단계에서는 RetrievalAugmentationAdvisor 의
 * ContextualQueryAugmenter 가 format 으로 같은 JSON 을 만들어 질문에 붙인다.
 */
public record SearchEvidence(List<PlanSummary> plans, int merged, List<Item> evidence) {

    public record Item(int rank, long storyId, String title, String url, List<Integer> matchedPlans,
                       String category, String summary, String communityReaction,
                       String practicalImplication, String passage) {
    }

    /** 2단계. searchIssues 가 Query 와 근거 Document 로 만든다. */
    public static SearchEvidence of(Query query, List<Document> evidence) {
        return of(query.context(), evidence);
    }

    /** 1단계. ChatTurn 이 응답 조각의 컨텍스트와 근거 Document 로 만든다. */
    public static SearchEvidence of(Map<String, Object> context, List<Document> evidence) {
        return new SearchEvidence(SearchContext.planSummaries(context), SearchContext.merged(context),
                items(evidence));
    }

    /** ContextualQueryAugmenter 의 documentFormatter. 근거 목록을 도구 결과와 같은 JSON 으로 적는다. */
    public static String format(List<Document> evidence) {
        return promptJson(items(evidence));
    }

    /** 근거 목록 JSON. format 과 확인 테스트가 함께 쓴다. */
    public static String promptJson(List<Item> items) {
        return JsonParser.toJson(Map.of("evidence", items));
    }

    private static List<Item> items(List<Document> evidence) {
        return evidence.stream()
                .map(EvidenceDocuments::item)
                .sorted(Comparator.comparingInt(Item::rank))
                .toList();
    }
}
