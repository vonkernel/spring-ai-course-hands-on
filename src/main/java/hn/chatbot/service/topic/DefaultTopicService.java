package hn.chatbot.service.topic;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.TopicSummarizer;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicDistribution;
import hn.chatbot.service.topic.model.TopicStories;
import hn.chatbot.service.topic.port.TopicQuery;
import hn.chatbot.service.topic.port.TopicQuery.StorySort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주제 탐색. 완성본이다 — 벡터도 LLM 도 쓰지 않는 메타데이터 집계라
 * Spring AI 학습 요소가 없다.
 *
 * 예외는 summarize 하나다. LLM 을 부르므로 ai/TopicSummarizer 에 위임하고,
 * 그 빈 구현을 수강생이 채운다.
 */
@Service
public class DefaultTopicService implements TopicService {

    private static final int SUMMARY_SAMPLE = 20;

    private final TopicQuery topicQuery;
    private final TopicSummarizer summarizer;

    public DefaultTopicService(TopicQuery topicQuery, TopicSummarizer summarizer) {
        this.topicQuery = topicQuery;
        this.summarizer = summarizer;
    }

    @Override
    public TopicDistribution distribution() {
        return topicQuery.distribution();
    }

    @Override
    public TopicStories stories(String techField, StorySort sort, int limit) {
        List<StorySummary> stories = topicQuery.stories(techField, sort, limit);
        return new TopicStories(techField, topicQuery.countByTechField(techField), stories);
    }

    @Override
    public String summarize(String techField) {
        // StorySummary 가 아니라 ai/ 의 입력 모델로 옮겨 넘긴다. 요약과 커뮤니티 반응을 함께 준다.
        List<AnalysisTarget> targets = topicQuery.stories(techField, StorySort.SCORE, SUMMARY_SAMPLE).stream()
                .map(s -> AnalysisTarget.forTopic(s.storyId(), s.title(), s.summary(), s.communityReaction()))
                .toList();

        return summarizer.summarize(techField, targets);
    }

    @Override
    public int count(String techField) {
        return topicQuery.count(techField);
    }
}
