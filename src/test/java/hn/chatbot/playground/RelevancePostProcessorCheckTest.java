package hn.chatbot.playground;

import hn.chatbot.search.Candidate;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchService;
import hn.chatbot.service.chat.RelevancePostProcessor;
import hn.chatbot.service.chat.SearchEvidence;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RelevancePostProcessor 확인. SearchService 가 찾은 후보를 넘겨, 질문에 맞는 것만 골라
 * 근거 카드 내용을 채운 Document 로 돌려주는지 본다. 적재된 데이터와 SearchService 구현이 있어야 한다.
 *
 *   ./gradlew playground --tests '*RelevancePostProcessorCheckTest'
 */
@Tag("playground")
@SpringBootTest
class RelevancePostProcessorCheckTest {

    @Autowired SearchService searchService;
    @Autowired RelevancePostProcessor postProcessor;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @Test
    @DisplayName("후보 중 질문에 맞는 것을 골라 근거로 만든다")
    void selectsEvidence() {
        Query query = SearchContext.query("What are the limits of AI coding agents?", null, null, null);
        List<Document> candidates = searchService.retrieve(query);
        List<Long> candidateIds = candidates.stream().map(d -> Candidate.from(d).storyId()).toList();

        List<Document> evidence = postProcessor.process(query, candidates);
        List<SearchEvidence.Item> items = SearchEvidence.of(query, evidence).evidence();

        Playground.title("후보 " + candidates.size() + "건 → 근거 " + items.size() + "건");
        items.forEach(i -> System.out.printf("  %d. %d  %s  [%s]%n     대목: %s%n",
                i.rank(), i.storyId(), i.title(), i.category(), i.passage()));

        assertThat(items).as("후보가 있으면 최소 1건은 남아야 한다").isNotEmpty().hasSizeLessThanOrEqualTo(5);
        assertThat(items).extracting(SearchEvidence.Item::storyId).as("받은 후보의 id 만 돌려줘야 한다")
                .isSubsetOf(candidateIds).doesNotHaveDuplicates();
        assertThat(items).extracting(SearchEvidence.Item::rank).as("판단 순서대로 1 부터 번호가 매겨져야 한다")
                .containsExactlyElementsOf(IntStream.rangeClosed(1, items.size()).boxed().toList());
        assertThat(items).as("근거 카드 내용이 채워져야 한다")
                .allSatisfy(i -> {
                    assertThat(i.title()).isNotBlank();
                    assertThat(i.category()).isNotBlank();
                    assertThat(i.communityReaction()).isNotBlank();
                });
    }

    @Test
    @DisplayName("후보가 없으면 판단 없이 빈 목록을 돌려준다")
    void emptyCandidates() {
        assertThat(postProcessor.process(SearchContext.query("q", null, null, null), List.of())).isEmpty();
    }
}
