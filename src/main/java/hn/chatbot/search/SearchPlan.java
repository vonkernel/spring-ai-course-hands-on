package hn.chatbot.search;


import org.springframework.ai.rag.Query;

/**
 * 검색 계획 하나.
 *
 * 네 계획을 전부 수행하고 각각 상위 5건을 받는다. 하나가 0건이어도
 * 나머지가 채운다. 계획 1 은 질문에 저장된 키워드가 들어 있을 때만 맞는다.
 *
 * 입력은 Spring AI 의 Query 다. 검색어는 query.text(), 필터는 SearchContext.techField ·
 * SearchContext.category 로 읽는다. 계획 1 은 SearchContext.userQuestion 으로 사용자 원문을 읽는다.
 *
 * 필터: suitable 은 항상 건다. techField · category 는 값이 있을 때만 건다.
 * RetrievalAugmentationAdvisor 가 부를 때는 비어 있고, searchIssues 가 부를 때는 모델이 도구 인자로 채운다.
 *
 * 결과와 함께 실제로 실행한 조건을 돌려준다. 조건 문자열은 PlanConditions 로 만든다.
 */
public interface SearchPlan {

    int number();

    String name();

    PlanRun execute(Query query, int limit);
}
