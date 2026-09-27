package hn.chatbot.search.plan;

import hn.chatbot.search.PlanConditions;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchPlan;
import hn.chatbot.search.port.StoryIndexQuery;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 검색 계획 2 — 전문 검색.
 *
 * websearch_to_tsquery 로 어간을 추출해 맞춘다. ts_rank 는 IDF 가 없어 질의마다 범위가 달라진다.
 *
 * 완성본이다. 검색어(query.text())를 그대로 넘긴다. 파싱에 실패하면 예외가 아니라 0건이다.
 */
@Component
public class FullTextPlan implements SearchPlan {

    private final StoryIndexQuery index;

    public FullTextPlan(StoryIndexQuery index) {
        this.index = index;
    }

    @Override
    public int number() {
        return 2;
    }

    @Override
    public String name() {
        return "전문 검색";
    }

    @Override
    public PlanRun execute(Query query, int limit) {
        String text = query.text();
        String field = SearchContext.techField(query);
        String type = SearchContext.category(query);
        String condition = PlanConditions.fullText(text, field, type);
        if (text.isBlank()) {
            return new PlanRun(List.of(), condition);
        }
        return new PlanRun(index.byFullText(text, field, type, limit), condition);
    }
}
