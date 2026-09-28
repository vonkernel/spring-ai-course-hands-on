package hn.chatbot.persistence;

import hn.chatbot.persistence.repository.AnalysisRepository;
import hn.chatbot.persistence.repository.BodyChunkRepository;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicCount;
import hn.chatbot.service.topic.model.TopicCounts;
import hn.chatbot.service.topic.model.TopicDistribution;
import hn.chatbot.service.topic.port.TopicQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TopicQuery 포트의 영속 어댑터. 전부 메타데이터 집계이고 벡터도 LLM 도 쓰지 않는다.
 *
 * 표시용 한글 이름(label)은 여기서 붙이지 않는다. 컨트롤러의 몫이다.
 */
@Component
public class JpaTopicQuery implements TopicQuery {

    private final AnalysisRepository analyses;
    private final BodyChunkRepository chunks;

    public JpaTopicQuery(AnalysisRepository analyses, BodyChunkRepository chunks) {
        this.analyses = analyses;
        this.chunks = chunks;
    }

    @Override
    public TopicDistribution distribution() {
        List<TopicCount> techFields = analyses.countByTechField().stream()
                .map(r -> new TopicCount(r.getValue(), (int) r.getCount())).toList();
        List<TopicCount> categories = analyses.countByCategory().stream()
                .map(r -> new TopicCount(r.getValue(), (int) r.getCount())).toList();

        TopicCounts counts = new TopicCounts(
                analyses.countSuitable(),
                techFields.size(),
                categories.size(),
                (int) chunks.count(),
                analyses.countDistinctKeywords());

        return new TopicDistribution(counts, techFields, categories);
    }

    @Override
    public List<StorySummary> stories(String techField, StorySort sort, int limit) {
        return analyses.findStories(blankToNull(techField), sort == StorySort.RECENT, limit).stream()
                .map(r -> new StorySummary(
                        r.getStoryId(), r.getTitle(), r.getUrl(), r.getSummary(),
                        r.getScore(), r.getCommentCount(), r.getCategory(),
                        r.getKeywords() == null ? List.of() : List.of(r.getKeywords()),
                        r.getCommunityReaction()))
                .toList();
    }

    @Override
    public int countByTechField(String techField) {
        return analyses.countSuitableByTechField(blankToNull(techField));
    }

    @Override
    public int count(String techField) {
        String value = blankToNull(techField);
        return value == null ? analyses.countSuitable() : analyses.countSuitableByTechField(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
