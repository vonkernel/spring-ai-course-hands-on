package hn.chatbot.service.topic.port;

import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicDistribution;

import java.util.List;

/**
 * 주제 탐색이 필요로 하는 조회 포트. 전부 메타데이터 집계이고 벡터도 LLM 도 쓰지 않는다.
 */
public interface TopicQuery {

    TopicDistribution distribution();

    /** sort 는 SCORE 또는 RECENT 다. */
    List<StorySummary> stories(String techField, StorySort sort, int limit);

    int countByTechField(String techField);

    /** 기술 분야의 적합 스토리 건수. null 이거나 빈 값이면 전체 건수다. */
    int count(String techField);

    enum StorySort {
        SCORE, RECENT
    }
}
