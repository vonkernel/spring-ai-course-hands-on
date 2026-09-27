package hn.chatbot.playground;

import hn.chatbot.search.Candidate;
import hn.chatbot.search.PlanSummary;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchService 확인. 네 계획을 모두 수행하고 중복을 제거한 후보를 Document 로 돌려주는지,
 * 계획별 요약을 Query 의 컨텍스트에 기록하는지 본다. 적재된 데이터가 있어야 한다.
 *
 *   ./gradlew playground --tests '*SearchServiceCheckTest'
 */
@Tag("playground")
@SpringBootTest
class SearchServiceCheckTest {

    @Autowired SearchService searchService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @Test
    @DisplayName("네 계획을 수행하고 중복을 제거한 후보를 돌려준다")
    void retrieves() {
        Query query = SearchContext.query("What are the limits of AI coding agents?", null, null, null);
        List<Document> documents = searchService.retrieve(query);
        List<PlanSummary> plans = SearchContext.planSummaries(query.context());
        List<Candidate> candidates = documents.stream().map(Candidate::from).toList();

        Playground.title("계획별 건수");
        plans.forEach(p -> System.out.printf("  계획 %d %-14s %d건  %s%n",
                p.plan(), p.name(), p.hits(), p.condition()));
        Playground.title("중복 제거 후 " + SearchContext.merged(query.context()) + "건");
        for (Candidate c : candidates) {
            System.out.printf("  %d  %s  계획 %s%n", c.storyId(), c.title(), c.matchedPlans());
        }

        assertThat(plans).as("네 계획의 요약이 컨텍스트에 기록돼야 한다").hasSize(4);
        assertThat(plans).as("계획마다 실행한 조건이 있어야 한다")
                .allSatisfy(p -> assertThat(p.condition()).isNotBlank());
        assertThat(SearchContext.merged(query.context())).isEqualTo(documents.size());
        assertThat(candidates).isNotEmpty().hasSizeLessThanOrEqualTo(20);
        assertThat(candidates).extracting(Candidate::storyId).doesNotHaveDuplicates();
        assertThat(candidates).as("후보에는 제목이 채워져 있어야 한다")
                .allSatisfy(c -> assertThat(c.title()).isNotBlank());
        assertThat(candidates).as("후보마다 찾아낸 계획 번호가 있어야 한다")
                .allSatisfy(c -> assertThat(c.matchedPlans()).isNotEmpty());
    }
}
