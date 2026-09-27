package hn.chatbot.playground;

import hn.chatbot.search.SearchContext;
import hn.chatbot.service.chat.ChatTurn;
import hn.chatbot.service.chat.IssueTools;
import hn.chatbot.service.chat.SearchEvidence;
import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.model.StorySummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * IssueTools 의 검색 도구와 기억 기록 확인.
 * searchIssues 가 근거를 만들어 사건으로 보내고 대화 기억에 기록하는지, listStories 가 결과를
 * 기록하는지 본다. 적재된 데이터가 있어야 한다.
 *
 *   ./gradlew playground --tests '*IssueToolsMemoryCheckTest'
 */
@Tag("playground")
@SpringBootTest
class IssueToolsMemoryCheckTest {

    @Autowired IssueTools tools;
    @Autowired ChatMemory chatMemory;
    @Autowired JdbcTemplate jdbc;

    private final String conversationId = UUID.randomUUID().toString();
    private final ChatTurn turn = new ChatTurn();
    private final ToolContext ctx = new ToolContext(Map.of(
            ChatMemory.CONVERSATION_ID, conversationId, ChatTurn.KEY, turn,
            SearchContext.USER_QUESTION, "코딩 에이전트의 한계가 뭐야?"));

    @BeforeEach
    void requireData() {
        Playground.requireLoadedData(jdbc);
    }

    @AfterEach
    void cleanUp() {
        chatMemory.clear(conversationId);
    }

    @Test
    @DisplayName("searchIssues 는 근거를 사건으로 보내고 카드 순서대로 기억에 기록한다")
    void searchIssuesPublishesAndRecords() {
        SearchEvidence evidence = tools.searchIssues("What are the limits of AI coding agents?", null, null, ctx);
        List<ChatEvent> events = turn.stream(Flux.empty()).collectList().block();

        List<Message> memory = chatMemory.get(conversationId);
        print(memory);

        assertThat(evidence.evidence()).as("근거가 있어야 한다").isNotEmpty();
        assertThat(evidence.plans()).as("계획별 조건이 있어야 한다")
                .hasSize(4).allSatisfy(p -> assertThat(p.condition()).isNotBlank());
        assertThat(events).as("plans 와 evidence 사건이 나가야 한다")
                .hasAtLeastOneElementOfType(ChatEvent.Plans.class)
                .hasAtLeastOneElementOfType(ChatEvent.Evidence.class);

        assertThat(memory).as("검색 기록이 기억에 남아야 한다").isNotEmpty();
        Message record = memory.get(memory.size() - 1);
        assertThat(record.getMessageType()).as("SystemMessage 가 아니라 AssistantMessage 로 기록한다")
                .isEqualTo(MessageType.ASSISTANT);
        assertThat(record.getText()).as("첫 번째 근거가 1번으로 기록돼야 한다")
                .contains("1. " + evidence.evidence().get(0).storyId());
    }

    @Test
    @DisplayName("listStories 는 결과를 기록하고 허용 범위 밖의 sortBy 와 빈 limit 에도 멈추지 않는다")
    void listStoriesRecords() {
        String techField = jdbc.queryForObject("""
                SELECT tech_field FROM analysis WHERE suitable AND tech_field IS NOT NULL
                GROUP BY tech_field ORDER BY count(*) DESC LIMIT 1
                """, String.class);

        List<StorySummary> stories = tools.listStories(techField, "SCORE", 3, ctx);
        System.out.println("listStories(" + techField + ", SCORE, 3): " + stories.size() + "건");

        assertThat(stories).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(chatMemory.get(conversationId)).as("목록 결과가 대화 기억에 남아야 한다").isNotEmpty();
        assertThatCode(() -> tools.listStories(techField, "banana", null, ctx))
                .as("모델이 엉뚱한 sortBy 를 채우거나 limit 을 비워도 기본값으로 처리해야 한다")
                .doesNotThrowAnyException();
    }

    private static void print(List<Message> memory) {
        Playground.title("대화 기억");
        memory.forEach(m -> System.out.println("  [" + m.getMessageType() + "] " + m.getText()));
    }
}
