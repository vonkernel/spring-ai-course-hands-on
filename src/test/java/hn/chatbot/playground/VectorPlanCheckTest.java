package hn.chatbot.playground;

import hn.chatbot.search.PlanHit;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.plan.BodyVectorPlan;
import hn.chatbot.search.plan.SummaryVectorPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BodyVectorPlan · SummaryVectorPlan 확인. 적재된 데이터가 있어야 한다.
 *
 *   ./gradlew playground --tests '*VectorPlanCheckTest'
 */
@Tag("playground")
@SpringBootTest
class VectorPlanCheckTest {

    private static final String QUERY = "What are the limits of AI coding agents?";

    @Autowired BodyVectorPlan bodyPlan;
    @Autowired SummaryVectorPlan summaryPlan;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @Test
    @DisplayName("본문 청크 검색은 스토리 단위로 묶어 돌려준다")
    void bodyPlan() {
        PlanRun run = bodyPlan.execute(SearchContext.query(QUERY, null, null, null), 5);
        List<PlanHit> hits = run.hits();
        print("계획 3 " + bodyPlan.name(), run);

        assertThat(hits).isNotEmpty().hasSizeLessThanOrEqualTo(5);
        assertThat(hits).extracting(PlanHit::storyId).as("같은 스토리가 두 번 나오면 안 된다").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("요약 검색은 상위 결과를 돌려준다")
    void summaryPlan() {
        PlanRun run = summaryPlan.execute(SearchContext.query(QUERY, null, null, null), 5);
        List<PlanHit> hits = run.hits();
        print("계획 4 " + summaryPlan.name(), run);

        assertThat(run.condition()).as("실행한 조건을 함께 돌려줘야 한다").contains("suitable");

        assertThat(hits).isNotEmpty().hasSizeLessThanOrEqualTo(5);
        assertThat(hits).extracting(PlanHit::storyId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("techField 를 주면 그 분야 안에서만 찾는다")
    void techFieldFilter() {
        String techField = jdbc.queryForObject("""
                SELECT tech_field FROM analysis WHERE suitable AND tech_field IS NOT NULL
                GROUP BY tech_field ORDER BY count(*) DESC LIMIT 1
                """, String.class);

        PlanRun run = summaryPlan.execute(SearchContext.query(QUERY, null, techField, ""), 5);
        List<PlanHit> hits = run.hits();
        print("계획 4, techField=" + techField, run);

        assertThat(hits).as("가장 많은 분야로 걸렀으므로 결과가 있어야 한다").isNotEmpty();

        for (PlanHit hit : hits) {
            String actual = jdbc.queryForObject("SELECT tech_field FROM analysis WHERE story_id = ?",
                    String.class, hit.storyId());
            assertThat(actual).isEqualTo(techField);
        }
    }

    @Test
    @DisplayName("category 를 주면 그 원문 타입 안에서만 찾는다")
    void categoryFilter() {
        String category = jdbc.queryForObject("""
                SELECT category FROM analysis WHERE suitable AND category IS NOT NULL
                GROUP BY category ORDER BY count(*) DESC LIMIT 1
                """, String.class);

        PlanRun run = bodyPlan.execute(SearchContext.query(QUERY, null, null, category), 5);
        print("계획 3, category=" + category, run);

        assertThat(run.hits()).as("가장 많은 원문 타입으로 걸렀으므로 결과가 있어야 한다").isNotEmpty();
        assertThat(run.condition()).contains(category);
        for (PlanHit hit : run.hits()) {
            String actual = jdbc.queryForObject("SELECT category FROM analysis WHERE story_id = ?",
                    String.class, hit.storyId());
            assertThat(actual).isEqualTo(category);
        }
    }

    private static void print(String label, PlanRun run) {
        Playground.title(label);
        System.out.println("  조건: " + run.condition());
        run.hits().forEach(h -> System.out.printf("  storyId=%d  score=%.4f%n", h.storyId(), h.score()));
    }
}
