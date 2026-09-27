package hn.chatbot.search.port;

import hn.chatbot.search.Candidate;
import hn.chatbot.search.PlanHit;

import java.util.List;

/**
 * 검색 계층이 소유하는 포트. 계획 1·2 가 쓰는 색인 조회와 후보 살 붙이기다.
 *
 * 구현은 persistence/ 가 맡는다. 검색 계층은 JPA 도 Spring Data 도 모른다.
 * 계획 3·4 는 Spring AI 의 VectorStore 를 직접 쓰므로 이 포트를 타지 않는다.
 */
public interface StoryIndexQuery {

    /**
     * 계획 1: keywords 배열 겹침. suitable 인 것만 대상이고, techField · category 가 null 이면 그 축으로 거르지 않는다.
     * 일치한 키워드가 많은 스토리가 앞에 온다.
     */
    List<PlanHit> byKeywords(List<String> keywords, String techField, String category, int limit);

    /** 계획 1 이 질문 안에서 찾을 키워드 목록. suitable 인 분석의 키워드를 저장된 표기 그대로, 중복 없이. */
    List<String> keywordVocabulary();

    /** 계획 2: websearch_to_tsquery 전문 검색. 점수는 ts_rank 다. 필터 규칙은 계획 1 과 같다. */
    List<PlanHit> byFullText(String query, String techField, String category, int limit);

    /**
     * 중복 제거를 마친 storyId 들에 제목과 요약을 채워 후보로 만든다.
     *
     * 순서는 입력 순서를 따른다. 분석 행이 없는 storyId 는 빠진다.
     * matchedPlans 는 비어 있다. 채우는 것은 호출하는 쪽의 몫이다.
     */
    List<Candidate> hydrate(List<Long> storyIds);
}
