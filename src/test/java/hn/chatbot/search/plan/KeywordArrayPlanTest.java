package hn.chatbot.search.plan;

import hn.chatbot.search.Candidate;
import hn.chatbot.search.PlanHit;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.port.StoryIndexQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordArrayPlanTest {

    private static final List<String> VOCABULARY = List.of(
            "Broadcom", "LG", "VMware", "coding agents", "digital sovereignty", "open source");

    @Test
    @DisplayName("두 단어 이상인 키워드도 질문 안에서 찾는다")
    void multiWordKeywords() {
        assertThat(KeywordArrayPlan.keywordsIn("digital sovereignty", VOCABULARY))
                .containsExactly("digital sovereignty");
        assertThat(KeywordArrayPlan.keywordsIn("요즘 coding agents 한계가 뭐야", VOCABULARY))
                .containsExactly("coding agents");
    }

    @Test
    @DisplayName("대소문자를 가리지 않고, 붙은 한글 조사는 경계로 본다")
    void caseAndKoreanParticles() {
        assertThat(KeywordArrayPlan.keywordsIn("vmware", VOCABULARY)).containsExactly("VMware");
        assertThat(KeywordArrayPlan.keywordsIn("VMware의 라이선스 변경", VOCABULARY)).containsExactly("VMware");
        assertThat(KeywordArrayPlan.keywordsIn("Broadcom 과 VMware", VOCABULARY))
                .containsExactly("Broadcom", "VMware");
    }

    @Test
    @DisplayName("영문 단어 안에 들어 있는 것은 키워드로 보지 않는다")
    void wordBoundary() {
        assertThat(KeywordArrayPlan.keywordsIn("new algorithm for opensource", VOCABULARY)).isEmpty();
    }

    @Test
    @DisplayName("모델이 다듬은 검색어가 아니라 사용자 원문에서 찾고, 찾은 표기로 조회한다")
    void usesUserQuestion() {
        RecordingIndex index = new RecordingIndex();
        KeywordArrayPlan plan = new KeywordArrayPlan(index);

        PlanRun run = plan.execute(SearchContext.query(
                "What are the recent issues around the company?", "vmware", null, "NEWS_REPORT"), 5);

        assertThat(index.keywords).containsExactly("VMware");
        assertThat(index.category).isEqualTo("NEWS_REPORT");
        assertThat(run.condition()).isEqualTo("keywords=[VMware], suitable, category=NEWS_REPORT");
    }

    private static final class RecordingIndex implements StoryIndexQuery {

        List<String> keywords = new ArrayList<>();
        String category;

        @Override
        public List<PlanHit> byKeywords(List<String> keywords, String techField, String category, int limit) {
            this.keywords = keywords;
            this.category = category;
            return List.of();
        }

        @Override
        public List<String> keywordVocabulary() {
            return VOCABULARY;
        }

        @Override
        public List<PlanHit> byFullText(String query, String techField, String category, int limit) {
            return List.of();
        }

        @Override
        public List<Candidate> hydrate(List<Long> storyIds) {
            return List.of();
        }
    }
}
