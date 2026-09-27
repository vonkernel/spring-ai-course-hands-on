package hn.chatbot.search.plan;

import hn.chatbot.search.PlanConditions;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchPlan;
import hn.chatbot.search.port.StoryIndexQuery;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 검색 계획 1 — 키워드 완전 일치.
 *
 * 배열 겹침(&&)이라 형태소 분석이 필요 없다. 질의가 자연어 문장이면 대부분 0건이고 고유명사 질의에서 가장 정확하다.
 *
 * 완성본이다. 모델이 다듬은 검색어가 아니라 사용자 원문(SearchContext.userQuestion)을 공백과 문장 부호로 잘라 그대로 키워드로 넘긴다. 대소문자를 바꾸지 않으므로
 * 분석 단계가 저장한 표기(VMware, pgvector)와 같게 물어야 걸린다.
 *
 * 벡터 계획(3 · 4)을 구현할 때 결과와 실행 조건을 함께 돌려주는 형태의 참고가 된다.
 */
@Component
public class KeywordArrayPlan implements SearchPlan {

    private static final Pattern TOKEN_DELIMITER = Pattern.compile("[\\s,;:!?()\\[\\]\"']+");

    private final StoryIndexQuery index;

    public KeywordArrayPlan(StoryIndexQuery index) {
        this.index = index;
    }

    @Override
    public int number() {
        return 1;
    }

    @Override
    public String name() {
        return "키워드 완전 일치";
    }

    @Override
    public PlanRun execute(Query query, int limit) {
        String question = SearchContext.userQuestion(query);
        List<String> keywords = Arrays.stream(TOKEN_DELIMITER.split(question))
                .filter(t -> t.length() >= 2)
                .distinct()
                .toList();
        String field = SearchContext.techField(query);
        String type = SearchContext.category(query);
        return new PlanRun(index.byKeywords(keywords, field, type, limit),
                PlanConditions.keywords(keywords, field, type));
    }
}
