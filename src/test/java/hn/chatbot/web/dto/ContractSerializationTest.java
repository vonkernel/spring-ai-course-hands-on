package hn.chatbot.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hn.chatbot.service.setup.model.LogKind;
import hn.chatbot.service.setup.model.LogState;
import hn.chatbot.service.setup.model.SetupState;
import hn.chatbot.service.setup.model.StageState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 회귀 시험. 기대값은 api-design.md 의 JSON 예시를 그대로 옮긴 것이다.
 *
 * DTO 는 컨트롤러 · 프론트엔드 · 서비스 셋에 동시에 물려 있고, 프론트엔드는
 * 빌드 산출물까지 커밋된다. 필드 이름이나 중첩 구조가 조용히 바뀌면 화면이 깨지는데,
 * 그 사실이 프론트를 다시 빌드할 때까지 드러나지 않는다. 이 시험이 그 사이를 막는다.
 */
class ContractSerializationTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    @DisplayName("SetupStatus — 필드 이름과 2계층 제외 사유 구조가 문서와 같다")
    void setupStatus() throws Exception {
        SetupStatus status = new SetupStatus(
                SetupState.RUNNING, 100, 60, 3, 8, 24, 5, 63, 80,
                List.of(new StageStatus(1, "수집", 100, StageState.DONE, 1.0),
                        new StageStatus(5, "LLM 분석", 71, StageState.RUNNING, 0.90)),
                new SetupStatus.Exclusions(
                        new SetupStatus.ExclusionGroup(21, Map.of("TOO_SHORT", 15, "META_ONLY", 3,
                                "NO_BODY", 2, "PDF", 1)),
                        new SetupStatus.ExclusionGroup(3, Map.of("NON_TECHNICAL", 2, "DUPLICATE", 1))),
                List.of(new LogEntry("17:42:08", LogKind.ANALYZE, 49605767L,
                        "Mistral raises €3B", "AI_LLM", LogState.OK)));

        var json = mapper.readTree(mapper.writeValueAsString(status));

        assertThat(json.fieldNames()).toIterable().containsExactly(
                "state", "target", "completed", "skipped", "inProgress", "excluded", "pending",
                "searchableStories", "etaSeconds", "stages", "exclusions", "recentLogs");
        assertThat(json.get("state").asText()).isEqualTo("RUNNING");
        assertThat(json.get("exclusions").get("mechanical").get("reasons").get("TOO_SHORT").asInt())
                .isEqualTo(15);
        assertThat(json.get("stages").get(0).fieldNames()).toIterable()
                .containsExactly("step", "name", "count", "state", "progress");
        assertThat(json.get("recentLogs").get(0).fieldNames()).toIterable()
                .containsExactly("at", "kind", "storyId", "title", "meta", "state");
    }

    @Test
    @DisplayName("SetupStatus — 진행바가 의존하는 항등식")
    void progressIdentity() {
        SetupStatus s = new SetupStatus(SetupState.RUNNING, 100, 60, 3, 8, 24, 5, 63, 80,
                List.of(), new SetupStatus.Exclusions(
                        new SetupStatus.ExclusionGroup(0, Map.of()),
                        new SetupStatus.ExclusionGroup(0, Map.of())),
                List.of());

        assertThat(s.completed() + s.skipped() + s.inProgress() + s.excluded() + s.pending())
                .isEqualTo(s.target());
    }

    @Test
    @DisplayName("etaSeconds 는 산출 불가 시 null 로 나간다")
    void nullEta() throws Exception {
        SetupStatus s = new SetupStatus(SetupState.IDLE, 0, 0, 0, 0, 0, 0, 0, null,
                List.of(), new SetupStatus.Exclusions(
                        new SetupStatus.ExclusionGroup(0, Map.of()),
                        new SetupStatus.ExclusionGroup(0, Map.of())),
                List.of());

        assertThat(mapper.writeValueAsString(s)).contains("\"etaSeconds\":null");
    }

    @Test
    @DisplayName("TopicStats — stats 가 중첩 객체로 나간다")
    void topicStats() throws Exception {
        TopicStats stats = new TopicStats(
                new TopicStats.Stats(63, 10, 9, 412, 487),
                List.of(new Facet("AI_LLM", "AI/LLM", 18)),
                List.of(new Facet("OPINION_ESSAY", "의견·에세이", 24)));

        var json = mapper.readTree(mapper.writeValueAsString(stats));

        assertThat(json.fieldNames()).toIterable().containsExactly("stats", "techFields", "categories");
        assertThat(json.get("stats").fieldNames()).toIterable()
                .containsExactly("stories", "techFields", "categories", "chunks", "keywords");
        assertThat(json.get("techFields").get(0).fieldNames()).toIterable()
                .containsExactly("value", "label", "count");
    }

    @Test
    @DisplayName("EvidenceCard — 화면이 읽는 필드가 모두 있다")
    void evidenceCard() throws Exception {
        EvidenceCard card = new EvidenceCard(49605767L, "Mistral raises €3B",
                "https://example.com", "example.com", List.of(3, 4), "NEWS_REPORT",
                "요약", "커뮤니티 반응", null, "원문 대목");

        var json = mapper.readTree(mapper.writeValueAsString(card));

        assertThat(json.fieldNames()).toIterable().containsExactly(
                "storyId", "title", "url", "domain", "matchedPlans", "category",
                "summary", "communityReaction", "practicalImplication", "passage");
        assertThat(json.get("practicalImplication").isNull()).isTrue();
    }

    @Test
    @DisplayName("StoryBrief — 목록 단계에서 communityReaction 을 함께 내린다")
    void storyBrief() throws Exception {
        StoryBrief brief = new StoryBrief(1L, "제목", "https://example.com", "example.com",
                300, 120, "NEWS_REPORT", List.of("VMware"), "반응");

        assertThat(mapper.readTree(mapper.writeValueAsString(brief)).fieldNames()).toIterable()
                .containsExactly("storyId", "title", "url", "domain", "score",
                        "commentCount", "category", "keywords", "communityReaction");
    }
}
