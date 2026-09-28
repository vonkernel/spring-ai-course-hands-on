package hn.chatbot.playground;

import hn.chatbot.service.chat.ChatTurn;
import hn.chatbot.service.chat.CommentView;
import hn.chatbot.service.chat.SearchEvidence;
import hn.chatbot.service.chat.StoryDetail;
import hn.chatbot.service.chat.StoryTools;
import hn.chatbot.service.chat.model.ChatEvent;
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

/**
 * StoryTools 의 도구 세 개 확인. 도구로 등록됐는지, 값을 돌려주는지,
 * searchStories 가 결과를 ChatTurn 에 넘기는지 본다. 적재된 데이터가 있어야 한다.
 *
 * ChatTurn 에 넘긴 결과는 앱의 대화 기억이 아니라 테스트용 메모리 기억에 기록해 확인한다.
 *
 *   ./gradlew playground --tests '*StoryToolsCheckTest'
 */
@Tag("playground")
@SpringBootTest
class StoryToolsCheckTest {

    private static final String CONVERSATION_ID = "story-tools-check";

    @Autowired StoryTools tools;
    @Autowired JdbcTemplate jdbc;

    private final ChatMemory memory = MessageWindowChatMemory.builder().build();
    private final ChatTurn turn = new ChatTurn(memory, CONVERSATION_ID);
    private final ToolContext ctx = new ToolContext(Map.of(
            ChatTurn.KEY, turn, StoryTools.USER_QUESTION, "코딩 에이전트의 한계가 뭐야?"));

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @Test
    @DisplayName("도구 세 개가 설명과 함께 등록돼 있다")
    void registered() {
        List<ToolCallback> callbacks = List.of(ToolCallbacks.from(tools));

        Playground.title("등록된 도구");
        callbacks.forEach(c -> System.out.printf("  %-16s %s%n",
                c.getToolDefinition().name(), c.getToolDefinition().description()));

        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
                .contains("searchStories", "getStoryDetail", "getComments");
        // description 을 비워 두면 Spring AI 가 메서드 이름으로 채운다. 그 값으로는 모델이 도구를 고를 수 없다.
        assertThat(callbacks).as("모델은 설명을 보고 도구를 고른다. 설명 문구를 직접 작성해야 한다")
                .allSatisfy(c -> assertThat(c.getToolDefinition().description())
                        .hasSizeGreaterThan(20)
                        .isNotEqualToIgnoringCase(c.getToolDefinition().name()));
    }

    @Test
    @DisplayName("상세와 댓글 도구가 값을 돌려준다")
    void returnsValues() {
        long storyId = jdbc.queryForObject(
                "SELECT story_id FROM analysis WHERE suitable ORDER BY story_id LIMIT 1", Long.class);

        StoryDetail detail = tools.getStoryDetail(storyId);
        List<CommentView> comments = tools.getComments(storyId, 3);

        System.out.println("getStoryDetail : " + detail.title());
        System.out.println("getComments    : " + comments.size() + "건");

        assertThat(detail.storyId()).isEqualTo(storyId);
        assertThat(comments).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("searchStories 는 근거를 돌려주고 결과를 ChatTurn 에 넘긴다")
    void searchStoriesPublishes() {
        SearchEvidence evidence = tools.searchStories("What are the limits of AI coding agents?", null, null, ctx);
        List<ChatEvent> events = turn.stream(Flux.empty()).collectList().block();
        List<Message> recorded = memory.get(CONVERSATION_ID);
        Playground.title("ChatTurn 이 남긴 기록");
        recorded.forEach(m -> System.out.println("  [" + m.getMessageType() + "] " + m.getText()));

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
}
