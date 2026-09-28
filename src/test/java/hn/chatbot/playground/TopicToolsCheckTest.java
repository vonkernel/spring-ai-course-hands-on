package hn.chatbot.playground;

import hn.chatbot.service.chat.ChatTurn;
import hn.chatbot.service.chat.TopicTools;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TopicTools 의 도구 네 개와 TopicSummarizer 확인. 도구로 등록됐는지, 분야 목록 · 건수 · 목록을
 * 돌려주는지, listStories 가 결과를 ChatTurn 에 넘기는지, summarizeTopic 이 분야의 논의 흐름을
 * 산문으로 요약하는지 본다. 적재된 데이터가 있어야 한다.
 *
 *   ./gradlew playground --tests '*TopicToolsCheckTest'
 */
@Tag("playground")
@SpringBootTest
class TopicToolsCheckTest {

    private static final String CONVERSATION_ID = "topic-tools-check";

    @Autowired TopicTools tools;
    @Autowired JdbcTemplate jdbc;

    private final ChatMemory memory = MessageWindowChatMemory.builder().build();
    private final ToolContext ctx = new ToolContext(Map.of(ChatTurn.KEY, new ChatTurn(memory, CONVERSATION_ID)));

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @Test
    @DisplayName("도구 네 개가 설명과 함께 등록돼 있다")
    void registered() {
        List<ToolCallback> callbacks = List.of(ToolCallbacks.from(tools));

        Playground.title("등록된 도구");
        callbacks.forEach(c -> System.out.printf("  %-16s %s%n",
                c.getToolDefinition().name(), c.getToolDefinition().description()));

        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
                .contains("listTopics", "listStories", "countStories", "summarizeTopic");
        // description 을 비워 두면 Spring AI 가 메서드 이름으로 채운다. 그 값으로는 모델이 도구를 고를 수 없다.
        assertThat(callbacks).as("모델은 설명을 보고 도구를 고른다. 설명 문구를 직접 작성해야 한다")
                .allSatisfy(c -> assertThat(c.getToolDefinition().description())
                        .hasSizeGreaterThan(20)
                        .isNotEqualToIgnoringCase(c.getToolDefinition().name()));
    }

    @Test
    @DisplayName("분야 목록과 분야별 건수를 돌려준다")
    void countsByTopic() {
        List<TopicCount> topics = tools.listTopics();
        String techField = topics.get(0).value();
        int total = tools.countStories(null);
        int inField = tools.countStories(techField);

        System.out.println("listTopics              : " + topics);
        System.out.println("countStories(전체)       : " + total);
        System.out.println("countStories(" + techField + ") : " + inField);

        assertThat(topics).isNotEmpty();
        assertThat(inField).as("분야 건수는 listTopics 의 건수와 같아야 한다").isEqualTo(topics.get(0).count());
        assertThat(total).isGreaterThanOrEqualTo(inField);
        assertThat(tools.countStories("")).as("빈 문자열은 전체로 다룬다").isEqualTo(total);
    }

    @Test
    @DisplayName("listStories 는 결과를 ChatTurn 에 넘기고 허용 범위 밖의 sortBy 와 빈 limit 에도 멈추지 않는다")
    void listStoriesPublishes() {
        String techField = tools.listTopics().get(0).value();

        List<StorySummary> stories = tools.listStories(techField, "SCORE", 3, ctx);
        System.out.println("listStories(" + techField + ", SCORE, 3): " + stories.size() + "건");

        assertThat(stories).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(memory.get(CONVERSATION_ID)).as("ChatTurn.publishStories 로 목록을 넘겨야 한다").isNotEmpty();
        assertThatCode(() -> tools.listStories(techField, "banana", null, ctx))
                .as("모델이 엉뚱한 sortBy 를 채우거나 limit 을 비워도 기본값으로 처리해야 한다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("summarizeTopic 이 분야의 논의 흐름을 요약한다")
    void summarizes() {
        String techField = tools.listTopics().get(0).value();

        String summary = tools.summarizeTopic(techField);

        Playground.title("summarizeTopic(" + techField + ")");
        System.out.println(summary);

        assertThat(summary).isNotBlank();
    }
}
