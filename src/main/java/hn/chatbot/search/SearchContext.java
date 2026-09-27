package hn.chatbot.search;

import org.springframework.ai.rag.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 검색 한 번의 입력과 부산물을 Spring AI Query 의 컨텍스트에 싣는 키와 도우미. 완성본이다.
 *
 * Query.text() 는 검색어다. RetrievalAugmentationAdvisor 가 부르면 사용자 질문 그대로이고,
 * searchIssues 가 부르면 모델이 도구 인자로 다듬은 값이다. 계획 1 은 모델이 다듬기 전의 사용자 원문이 필요하므로
 * 원문을 따로 싣는다. 다듬은 문장으로는 사용자가 쓴 키워드가 그대로 남지 않는다.
 *
 * 검색 결과 외의 부산물(계획별 요약, 병합 건수)도 컨텍스트에 기록한다.
 * DocumentRetriever 는 List&lt;Document&gt; 만 돌려주기 때문이다.
 * RetrievalAugmentationAdvisor 는 Query 를 만들 때 요청 컨텍스트의 사본을 그대로 넣고,
 * 그 맵을 다시 요청 컨텍스트로 넘긴다. 그래서 여기 기록한 값이 응답 조각의 컨텍스트까지
 * 따라간다(Spring AI 1.1.8 기준, 문서화된 계약이 아니라 구현이 그렇다).
 *
 * 기록하려면 컨텍스트가 변경 가능한 맵이어야 한다. 직접 만들 때는 query(...) 를 쓴다.
 */
public final class SearchContext {

    /** 모델이 다듬기 전의 사용자 원문. 없으면 Query.text() 를 원문으로 본다. */
    public static final String USER_QUESTION = "hn_user_question";

    public static final String TECH_FIELD = "hn_tech_field";

    public static final String CATEGORY = "hn_category";

    /** SearchService 가 기록한다. 계획마다의 PlanSummary 목록. */
    public static final String PLAN_SUMMARIES = "hn_plan_summaries";

    /** SearchService 가 기록한다. 중복 제거 후 후보 수. */
    public static final String MERGED = "hn_merged";

    private SearchContext() {
    }

    /** 검색어와 원문 · 필터를 담은 Query. 비어 있는 값은 싣지 않는다. */
    public static Query query(String text, String userQuestion, String techField, String category) {
        Map<String, Object> context = new HashMap<>();
        putIfPresent(context, USER_QUESTION, userQuestion);
        putIfPresent(context, TECH_FIELD, techField);
        putIfPresent(context, CATEGORY, category);
        return Query.builder().text(text).context(context).build();
    }

    public static String userQuestion(Query query) {
        Object value = query.context().get(USER_QUESTION);
        return value instanceof String s && !s.isBlank() ? s : query.text();
    }

    public static String techField(Query query) {
        return text(query.context(), TECH_FIELD);
    }

    public static String category(Query query) {
        return text(query.context(), CATEGORY);
    }

    /** 계획별 요약과 병합 건수를 Query 의 컨텍스트에 남긴다. */
    public static void record(Query query, List<PlanSummary> plans, int merged) {
        query.context().put(PLAN_SUMMARIES, List.copyOf(plans));
        query.context().put(MERGED, merged);
    }

    @SuppressWarnings("unchecked")
    public static List<PlanSummary> planSummaries(Map<String, Object> context) {
        Object value = context.get(PLAN_SUMMARIES);
        return value instanceof List<?> list ? (List<PlanSummary>) list : List.of();
    }

    public static int merged(Map<String, Object> context) {
        Object value = context.get(MERGED);
        return value instanceof Integer n ? n : 0;
    }

    private static String text(Map<String, Object> context, String key) {
        Object value = context.get(key);
        return value instanceof String s ? PlanConditions.blankToNull(s) : null;
    }

    private static void putIfPresent(Map<String, Object> context, String key, String value) {
        String clean = PlanConditions.blankToNull(value);
        if (clean != null) {
            context.put(key, clean);
        }
    }
}
