package hn.chatbot.playground;

import hn.chatbot.service.chat.ChatTurn;
import hn.chatbot.service.chat.CommentView;
import hn.chatbot.service.chat.IssueTools;
import hn.chatbot.service.chat.SearchEvidence;
import hn.chatbot.service.chat.StoryDetail;
import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicDistribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * IssueTools 의 도구 여섯 개 확인. summarizeTopic 은 TopicSummarizerCheckTest 가 본다.
 * 도구로 등록됐는지, 값을 돌려주는지, searchIssues · listStories 가 결과를 ChatTurn 에 넘기는지 본다.
 * 적재된 데이터가 있어야 한다.
 *
 * ChatTurn 에 넘긴 결과는 앱의 대화 기억이 아니라 테스트용 메모리 기억에 기록해 확인한다.
 *
 *   ./gradlew playground --tests '*IssueToolsCheckTest'
 */
@Tag("playground")
@SpringBootTest
class IssueToolsCheckTest {

    private static final String CONVERSATION_ID = "issue-tools-check";

    @Autowired IssueTools tools;
    @Autowired JdbcTemplate jdbc;

    private final ChatMemory memory = MessageWindowChatMemory.builder().build();
    private final ChatTurn turn = new ChatTurn(memory, CONVERSATION_ID);
    private final ToolContext ctx = new ToolContext(Map.of(
            ChatTurn.KEY, turn, IssueTools.USER_QUESTION, "코딩 에이전트의 한계가 뭐야?"));

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @Test
    @DisplayName("도구 여섯 개가 설명과 함께 등록돼 있다")
    void registered() {
        List<ToolCallback> callbacks = List.of(ToolCallbacks.from(tools));

        Playground.title("등록된 도구");
        callbacks.forEach(c -> System.out.printf("  %-16s %s%n",
                c.getToolDefinition().name(), c.getToolDefinition().description()));

        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
                .contains("searchIssues", "countStories", "listStories", "getStoryDetail", "getComments", "listTopics");
        // description 을 비워 두면 Spring AI 가 메서드 이름으로 채운다. 그 값으로는 모델이 도구를 고를 수 없다.
        assertThat(callbacks).as("모델은 설명을 보고 도구를 고른다. 설명 문구를 직접 작성해야 한다")
                .allSatisfy(c -> assertThat(c.getToolDefinition().description())
                        .hasSizeGreaterThan(20)
                        .isNotEqualToIgnoringCase(c.getToolDefinition().name()));
    }

    @Test
    @DisplayName("조회 도구가 값을 돌려준다")
    void returnsValues() {
        long storyId = jdbc.queryForObject(
                "SELECT story_id FROM analysis WHERE suitable ORDER BY story_id LIMIT 1", Long.class);

        int count = tools.countStories(null, null);
        TopicDistribution topics = tools.listTopics();
        StoryDetail detail = tools.getStoryDetail(storyId);
        List<CommentView> comments = tools.getComments(storyId, 3);

        System.out.println("countStories   : " + count);
        System.out.println("listTopics     : " + topics.techFields());
        System.out.println("getStoryDetail : " + detail.title());
        System.out.println("getComments    : " + comments.size() + "건");

        assertThat(count).isPositive();
        assertThat(topics.techFields()).isNotEmpty();
        assertThat(detail.storyId()).isEqualTo(storyId);
        assertThat(comments).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("searchIssues 는 근거를 돌려주고 결과를 ChatTurn 에 넘긴다")
    void searchIssuesPublishes() {
        SearchEvidence evidence = tools.searchIssues("What are the limits of AI coding agents?", null, null, ctx);
        List<ChatEvent> events = turn.stream(Flux.empty()).collectList().block();
        List<Message> recorded = memory.get(CONVERSATION_ID);
        print(recorded);

        assertThat(evidence.evidence()).as("근거가 있어야 한다").isNotEmpty();
        assertThat(evidence.plans()).as("계획별 조건이 있어야 한다")
                .hasSize(4).allSatisfy(p -> assertThat(p.condition()).isNotBlank());
        assertThat(events).as("ChatTurn.publish 로 넘겨야 plans 와 evidence 사건이 나간다")
                .hasAtLeastOneElementOfType(ChatEvent.Plans.class)
                .hasAtLeastOneElementOfType(ChatEvent.Evidence.class);
        assertThat(recorded).as("ChatTurn 에 넘긴 검색 결과가 기록돼야 한다").isNotEmpty();
        assertThat(recorded.get(recorded.size() - 1).getText()).as("첫 번째 근거가 1번으로 기록돼야 한다")
                .contains("1. " + evidence.evidence().get(0).storyId());
    }

    @Test
    @DisplayName("listStories 는 결과를 ChatTurn 에 넘기고 허용 범위 밖의 sortBy 와 빈 limit 에도 멈추지 않는다")
    void listStoriesPublishes() {
        String techField = jdbc.queryForObject("""
                SELECT tech_field FROM analysis WHERE suitable AND tech_field IS NOT NULL
                GROUP BY tech_field ORDER BY count(*) DESC LIMIT 1
                """, String.class);

        List<StorySummary> stories = tools.listStories(techField, "SCORE", 3, ctx);
        System.out.println("listStories(" + techField + ", SCORE, 3): " + stories.size() + "건");
        print(memory.get(CONVERSATION_ID));

        assertThat(stories).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(memory.get(CONVERSATION_ID)).as("ChatTurn.publishStories 로 목록을 넘겨야 한다").isNotEmpty();
        assertThatCode(() -> tools.listStories(techField, "banana", null, ctx))
                .as("모델이 엉뚱한 sortBy 를 채우거나 limit 을 비워도 기본값으로 처리해야 한다")
                .doesNotThrowAnyException();
    }

    private static void print(List<Message> recorded) {
        Playground.title("ChatTurn 이 남긴 기록");
        recorded.forEach(m -> System.out.println("  [" + m.getMessageType() + "] " + m.getText()));
    }
}
