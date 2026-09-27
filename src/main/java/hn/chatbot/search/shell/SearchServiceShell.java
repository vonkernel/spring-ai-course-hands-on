package hn.chatbot.search.shell;

import hn.chatbot.search.Candidate;
import hn.chatbot.search.PlanHit;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.PlanSummary;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchPlan;
import hn.chatbot.search.SearchService;
import hn.chatbot.search.port.StoryIndexQuery;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 계획마다 받는 상위 건수. */
    private static final int HITS_PER_PLAN = 5;

    /** 중복 제거 후 남기는 후보의 최대 건수. */
    private static final int MAX_MERGED = 20;

    private final List<SearchPlan> plans;
    private final StoryIndexQuery index;

    public SearchServiceShell(List<SearchPlan> plans, StoryIndexQuery index) {
        this.plans = plans;
        this.index = index;
    }

    @Override
    public List<Document> retrieve(Query query) {
        List<SearchPlan> ordered = plans.stream()
                .sorted(Comparator.comparingInt(SearchPlan::number))
                .toList();

        List<PlanSummary> summaries = new ArrayList<>();
        Map<Long, List<Integer>> matchedPlansByStory = new LinkedHashMap<>();

        for (SearchPlan plan : ordered) {
            PlanRun run = plan.execute(query, HITS_PER_PLAN);
            summaries.add(new PlanSummary(plan.number(), plan.name(), run.hits().size(), run.condition()));

            for (PlanHit hit : run.hits()) {
                List<Integer> matchedPlans = matchedPlansByStory.get(hit.storyId());
                if (matchedPlans != null) {
                    matchedPlans.add(plan.number());
                }
                else if (matchedPlansByStory.size() < MAX_MERGED) {
                    matchedPlansByStory.put(hit.storyId(), new ArrayList<>(List.of(plan.number())));
                }
            }
        }

        List<Long> storyIds = List.copyOf(matchedPlansByStory.keySet());
        List<Document> documents = index.hydrate(storyIds).stream()
                .map(candidate -> new Candidate(candidate.storyId(), candidate.title(), candidate.summary(),
                        matchedPlansByStory.get(candidate.storyId())).toDocument())
                .toList();

        SearchContext.record(query, summaries, storyIds.size());
        return documents;
    }
}
