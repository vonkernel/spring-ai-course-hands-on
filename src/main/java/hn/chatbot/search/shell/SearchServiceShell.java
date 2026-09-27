package hn.chatbot.search.shell;

import hn.chatbot.search.SearchPlan;
import hn.chatbot.search.SearchService;
import hn.chatbot.search.port.StoryIndexQuery;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 4개 계획을 전부 수행하고 storyId 기준으로 중복을 제거한다(최대 20건).
 *
 * 점수는 합치지 않는다. 불리언 · ts_rank · 코사인 유사도는 서로 비교할 수 없는 척도다.
 * 최종 순위는 RelevanceJudge 가 정한다.
 *
 * 계획마다 PlanSummary 를 남긴다. 건수와 함께 계획이 돌려준 실행 조건을 그대로 담는다.
 * 모은 요약과 병합 건수는 SearchContext.record 로 query 의 컨텍스트에 기록한다.
 *
 * 계획이 돌려주는 것은 storyId 와 점수뿐이다. 중복을 제거한 뒤
 * StoryIndexQuery.hydrate 로 제목과 요약을 채워 후보를 만들고, 후보마다 찾아낸 계획 번호를 기록한다.
 * 후보는 Candidate.toDocument 로 Document 로 바꿔 돌려준다.
 *
 * 계획 4개와 색인 조회가 주입돼 있다.
 */
@Service
public class SearchServiceShell implements SearchService {

    private final List<SearchPlan> plans;
    private final StoryIndexQuery index;

    public SearchServiceShell(List<SearchPlan> plans, StoryIndexQuery index) {
        this.plans = plans;
        this.index = index;
    }

    @Override
    public List<Document> retrieve(Query query) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
