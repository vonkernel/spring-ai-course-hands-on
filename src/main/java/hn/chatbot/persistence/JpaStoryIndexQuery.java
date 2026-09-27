package hn.chatbot.persistence;

import hn.chatbot.domain.Analysis;
import hn.chatbot.domain.Story;
import hn.chatbot.persistence.repository.AnalysisRepository;
import hn.chatbot.persistence.repository.ArticleRepository;
import hn.chatbot.persistence.repository.StoryRepository;
import hn.chatbot.search.Candidate;
import hn.chatbot.search.PlanHit;
import hn.chatbot.search.port.StoryIndexQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** StoryIndexQuery 포트의 영속 어댑터. 계획 1·2 의 네이티브 쿼리와 후보 살 붙이기를 감싼다. */
@Component
public class JpaStoryIndexQuery implements StoryIndexQuery {

    private final AnalysisRepository analyses;
    private final ArticleRepository articles;
    private final StoryRepository stories;

    public JpaStoryIndexQuery(AnalysisRepository analyses, ArticleRepository articles, StoryRepository stories) {
        this.analyses = analyses;
        this.articles = articles;
        this.stories = stories;
    }

    @Override
    public List<PlanHit> byKeywords(List<String> keywords, String techField, String category, int limit) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        return analyses.searchByKeywords(keywords.toArray(String[]::new), techField, category, limit).stream()
                .map(id -> new PlanHit(id, 0.0))   // 계획 1 은 점수가 없다
                .toList();
    }

    @Override
    public List<String> keywordVocabulary() {
        return analyses.findDistinctKeywords();
    }

    @Override
    public List<PlanHit> byFullText(String query, String techField, String category, int limit) {
        return articles.searchByFullText(query, techField, category, limit).stream()
                .map(r -> new PlanHit(r.getStoryId(), r.getScore()))
                .toList();
    }

    @Override
    public List<Candidate> hydrate(List<Long> storyIds) {
        Map<Long, String> titles = stories.findAllById(storyIds).stream()
                .collect(Collectors.toMap(Story::getId, Story::getTitle));
        Map<Long, Analysis> byId = analyses.findAllById(storyIds).stream()
                .collect(Collectors.toMap(Analysis::getStoryId, Function.identity()));

        return storyIds.stream()
                .filter(byId::containsKey)
                .map(id -> new Candidate(id, titles.get(id), byId.get(id).getSummary(), List.of()))
                .toList();
    }
}
