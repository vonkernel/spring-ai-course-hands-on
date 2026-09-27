package hn.chatbot.persistence.repository;

import hn.chatbot.domain.Analysis;
import hn.chatbot.domain.Article;
import hn.chatbot.domain.ArticleStatus;
import hn.chatbot.domain.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 계획 1·2 네이티브 쿼리가 실제 PostgreSQL 에서 파싱되고 타입 캐스팅이 맞는지 본다.
 * docker compose 로 띄운 DB 를 그대로 쓴다 (embedded DB 로 바꾸지 않는다).
 *
 * 데이터 셋업을 실행한 DB 에도 실제 스토리가 있으므로, 결과는 심어 둔 행의 포함 여부와
 * 증가분으로만 단정한다. 심어 둔 행은 테스트가 끝나면 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryQueryTest {

    @Autowired TestEntityManager em;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired ArticleRepository articleRepository;

    private int suitableBefore;
    private int aiBefore;
    private int aiDeepBefore;
    private int aiNewsBefore;

    @BeforeEach
    void seed() {
        suitableBefore = analysisRepository.countSuitable();
        aiBefore = analysisRepository.countSuitable("AI_LLM", null);
        aiDeepBefore = analysisRepository.countSuitable("AI_LLM", "TECHNICAL_DEEP_DIVE");
        aiNewsBefore = analysisRepository.countSuitable("AI_LLM", "NEWS_REPORT");

        em.persist(new Story(1L, "VMware licensing changes upset customers",
                "https://example.com/vmware", "alice", 300, 120, null, Instant.now()));
        em.persist(new Story(2L, "A deep dive into pgvector indexing",
                "https://example.com/pgvector", "bob", 210, 80, null, Instant.now()));

        em.persist(new Article(1L, ArticleStatus.PASS, "raw", 5000,
                "Broadcom changed VMware licensing and customers are migrating away."));
        em.persist(new Article(2L, ArticleStatus.PASS, "raw", 4000,
                "HNSW indexes in pgvector trade recall for speed during similarity search."));

        em.persist(new Analysis(1L, "VMware licensing shift", "NEWS_REPORT",
                "INFRASTRUCTURE_ENTERPRISE", new String[] {"VMware", "Broadcom", "licensing"},
                "Frustration", "Evaluate alternatives", true, null));
        em.persist(new Analysis(2L, "pgvector indexing internals", "TECHNICAL_DEEP_DIVE",
                "AI_LLM", new String[] {"pgvector", "HNSW", "embeddings"},
                "Appreciation", null, true, null));
        // 검색 대상에서 빠져야 하는 행
        em.persist(new Story(3L, "A poem about clouds", null, "carol", 10, 2, null, Instant.now()));
        em.persist(new Analysis(3L, null, null, null, new String[] {"VMware", "cloud poetry"},
                null, null, false, "기술과 무관한 주제"));
        em.flush();
    }

    @Test
    @DisplayName("계획 1 — keywords && text[] 배열 겹침으로 찾는다")
    void keywordArrayOverlap() {
        List<Long> hits = analysisRepository.searchByKeywords(new String[] {"VMware"}, null, null, 100);
        assertThat(hits).contains(1L).doesNotContain(3L);   // suitable=false 인 3L 은 빠진다

        assertThat(analysisRepository.searchByKeywords(new String[] {"HNSW", "Broadcom"}, null, null, 100))
                .contains(1L, 2L);
        assertThat(analysisRepository.searchByKeywords(new String[] {"없는키워드"}, null, null, 100))
                .isEmpty();
    }

    @Test
    @DisplayName("계획 1 — 일치한 키워드가 많은 스토리가 앞에 온다")
    void keywordArrayOrdersByMatchCount() {
        List<Long> hits = analysisRepository.searchByKeywords(new String[] {"VMware", "pgvector", "HNSW"}, null, null, 100);

        assertThat(hits).contains(1L, 2L);
        assertThat(hits.indexOf(2L)).as("두 개가 맞은 2번이 한 개가 맞은 1번보다 앞").isLessThan(hits.indexOf(1L));
    }

    @Test
    @DisplayName("계획 1 — 키워드 목록은 suitable 인 분석의 키워드만 중복 없이 모은다")
    void keywordVocabulary() {
        List<String> vocabulary = analysisRepository.findDistinctKeywords();

        assertThat(vocabulary).contains("VMware", "Broadcom", "pgvector", "HNSW").doesNotHaveDuplicates();
        assertThat(vocabulary).as("suitable=false 인 행에만 있는 키워드").doesNotContain("cloud poetry");
    }

    @Test
    @DisplayName("계획 1 — techField 를 주면 그 분야만 찾는다")
    void keywordArrayOverlapWithTechField() {
        assertThat(analysisRepository.searchByKeywords(new String[] {"HNSW", "Broadcom"}, "AI_LLM", null, 100))
                .contains(2L).doesNotContain(1L);
    }

    @Test
    @DisplayName("계획 2 — websearch_to_tsquery 전문 검색, 어간 추출이 동작한다")
    void fullTextSearch() {
        assertThat(storyIds(articleRepository.searchByFullText("licensing", null, null, 100))).contains(1L).doesNotContain(2L);
        // migrating -> migrat 로 어간 추출돼 migrate 질의에도 걸린다
        assertThat(storyIds(articleRepository.searchByFullText("migrate", null, null, 100))).contains(1L);
        assertThat(storyIds(articleRepository.searchByFullText("similarity search", null, null, 100))).contains(2L).doesNotContain(1L);
        // 파싱 실패해도 예외가 아니라 0건이다
        assertThat(articleRepository.searchByFullText("\"unbalanced quote", null, null, 5)).isEmpty();
    }

    @Test
    @DisplayName("계획 2 — ts_rank 점수를 돌려주고 techField 로 거른다")
    void fullTextScoreAndTechField() {
        assertThat(articleRepository.searchByFullText("licensing", null, null, 100))
                .allSatisfy(r -> assertThat(r.getScore()).isPositive());
        assertThat(storyIds(articleRepository.searchByFullText("licensing", "AI_LLM", null, 100))).doesNotContain(1L);
    }

    private static List<Long> storyIds(List<ArticleRepository.RankedStory> rows) {
        return rows.stream().map(ArticleRepository.RankedStory::getStoryId).toList();
    }

    @Test
    @DisplayName("계획 1 · 2 — category 를 주면 그 원문 타입만 찾는다")
    void categoryFilter() {
        assertThat(analysisRepository.searchByKeywords(new String[] {"HNSW", "Broadcom"}, null, "NEWS_REPORT", 100))
                .contains(1L).doesNotContain(2L);
        assertThat(storyIds(articleRepository.searchByFullText("licensing", null, "TECHNICAL_DEEP_DIVE", 100)))
                .doesNotContain(1L);
    }

    @Test
    @DisplayName("countStories 용 건수 — 비운 축은 거르지 않는다")
    void countByTechFieldAndCategory() {
        assertThat(analysisRepository.countSuitable(null, null)).isEqualTo(suitableBefore + 2);
        assertThat(analysisRepository.countSuitable("AI_LLM", null)).isEqualTo(aiBefore + 1);
        assertThat(analysisRepository.countSuitable("AI_LLM", "TECHNICAL_DEEP_DIVE")).isEqualTo(aiDeepBefore + 1);
        assertThat(analysisRepository.countSuitable("AI_LLM", "NEWS_REPORT")).isEqualTo(aiNewsBefore);
    }

    @Test
    @DisplayName("데이터 준비 판정 — suitable 인 것만 센다")
    void countSuitable() {
        assertThat(analysisRepository.countSuitable()).isEqualTo(suitableBefore + 2);
    }

    @Test
    @DisplayName("분포 조회")
    void facets() {
        assertThat(analysisRepository.countByTechField())
                .extracting(AnalysisRepository.FacetRow::getValue)
                .contains("INFRASTRUCTURE_ENTERPRISE", "AI_LLM");
        assertThat(analysisRepository.countByCategory())
                .extracting(AnalysisRepository.FacetRow::getValue)
                .contains("NEWS_REPORT", "TECHNICAL_DEEP_DIVE");
    }

    @Test
    @DisplayName("body_tsv 는 생성 컬럼이라 body 를 넣으면 DB 가 채운다")
    void generatedTsvector() {
        Object filled = em.getEntityManager()
                .createNativeQuery("SELECT length(body_tsv::text) > 0 FROM article WHERE story_id = 1")
                .getSingleResult();
        assertThat(filled).isEqualTo(Boolean.TRUE);
    }
}
