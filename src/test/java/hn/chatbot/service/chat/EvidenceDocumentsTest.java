package hn.chatbot.service.chat;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.RelevantStory;
import hn.chatbot.search.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceDocumentsTest {

    static final List<Document> CANDIDATES = List.of(
            new Candidate(1L, "one", "s1", List.of(3)).toDocument(),
            new Candidate(2L, "two", "s2", List.of(3, 4)).toDocument());

    static final List<StoryDetail> DETAILS = List.of(
            new StoryDetail(1L, "one", "https://a", "s1", "r1", null, "AI_LLM", "NEWS_REPORT", List.of()),
            new StoryDetail(2L, "two", "https://b", "s2", "r2", "p2", "AI_LLM", "OPINION_ESSAY", List.of()));

    @Test
    @DisplayName("판단 입력은 원문 앞부분이 있는 후보만 만든다")
    void targets() {
        List<AnalysisTarget> targets = EvidenceDocuments.targets(CANDIDATES, Map.of(2L, "body"));

        assertThat(targets).extracting(AnalysisTarget::storyId).containsExactly(2L);
        assertThat(targets.get(0).excerpt()).isEqualTo("body");
        assertThat(EvidenceDocuments.storyIds(CANDIDATES)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("판단 순서대로 번호를 매기고, 목록에 없는 id 와 원문에 없는 대목은 버린다")
    void evidence() {
        List<RelevantStory> judged = List.of(
                new RelevantStory(2L, "agents   lose\ncontext"),
                new RelevantStory(99L, "made up"),
                new RelevantStory(1L, "not in the text"));
        Map<Long, String> excerpts = Map.of(1L, "something else", 2L, "Coding agents lose context on refactors.");

        List<SearchEvidence.Item> items = EvidenceDocuments.evidence(CANDIDATES, judged, DETAILS, excerpts).stream()
                .map(EvidenceDocuments::item)
                .toList();

        assertThat(items).extracting(SearchEvidence.Item::storyId).containsExactly(2L, 1L);
        assertThat(items).extracting(SearchEvidence.Item::rank).containsExactly(1, 2);
        assertThat(items.get(0).passage()).isEqualTo("agents   lose\ncontext");
        assertThat(items.get(0).matchedPlans()).containsExactly(3, 4);
        assertThat(items.get(0).practicalImplication()).isEqualTo("p2");
        assertThat(items.get(1).passage()).isNull();
        assertThat(items.get(1).practicalImplication()).isNull();
    }

    @Test
    @DisplayName("대목은 300자까지만 남긴다")
    void passageLimit() {
        String longText = "a".repeat(400);
        assertThat(EvidenceDocuments.verifiedPassage(longText, longText)).hasSize(300);
        assertThat(EvidenceDocuments.verifiedPassage(" ", longText)).isNull();
        assertThat(EvidenceDocuments.verifiedPassage("x", null)).isNull();
    }
}
