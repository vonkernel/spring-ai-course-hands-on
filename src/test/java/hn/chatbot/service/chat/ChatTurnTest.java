package hn.chatbot.service.chat;

import hn.chatbot.ai.RelevantStory;
import hn.chatbot.search.PlanSummary;
import hn.chatbot.search.SearchContext;
import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.model.StorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTurnTest {

    private static final SearchEvidence EVIDENCE = new SearchEvidence(
            List.of(new PlanSummary(1, "키워드 완전 일치", 0, "keywords=[a], suitable")), 1,
            List.of(new SearchEvidence.Item(1, 7L, "t", "u", List.of(1), "NEWS_REPORT", "s", "r", null, "p"),
                    new SearchEvidence.Item(2, 3L, "t2", "u2", List.of(3), "NEWS_REPORT", "s", "r", null, "p")));

    @Test
    @DisplayName("도구가 방출한 사건이 token 보다 앞서고 끝에 done 이 붙는다")
    void ordersEvents() {
        ChatTurn turn = new ChatTurn();
        SearchEvidence evidence = EVIDENCE;

        // 모델이 도구를 호출한 뒤에야 token 이 나오는 상황을 흉내 낸다.
        Flux<String> tokens = Flux.defer(() -> {
            turn.publish(SearchContext.query("q", null, null, null), evidence);
            return Flux.just("안", "녕").delayElements(Duration.ofMillis(10));
        });

        List<ChatEvent> events = turn.stream(tokens).collectList().block();

        assertThat(events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Plans", "Evidence", "Token", "Token", "Completed");
        ChatEvent.Plans plans = (ChatEvent.Plans) events.get(0);
        assertThat(plans.plans().get(0).condition()).isEqualTo("keywords=[a], suitable");
    }

    @Test
    @DisplayName("기억을 받은 ChatTurn 은 검색 결과를 카드 순서대로 AssistantMessage 로 기록한다")
    void recordsSearch() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        ChatTurn turn = new ChatTurn(memory, "c1");

        turn.publish(SearchContext.query("coding agent limits", null, null, "OFFICIAL_ANNOUNCEMENT"), EVIDENCE);

        List<Message> recorded = memory.get("c1");
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(recorded.get(0).getText())
                .contains("query=\"coding agent limits\"", "category=OFFICIAL_ANNOUNCEMENT", "1. 7 t", "2. 3 t2");
    }

    @Test
    @DisplayName("기억을 받은 ChatTurn 은 목록 결과를 순서대로 기록한다")
    void recordsStories() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        ChatTurn turn = new ChatTurn(memory, "c1");
        StorySummary story = new StorySummary(7L, "t", null, "s", 0, 0, "NEWS_REPORT", List.of(), "r");

        turn.publishStories("AI_LLM", "SCORE", List.of(story));

        assertThat(memory.get("c1")).extracting(Message::getText).singleElement().asString()
                .contains("techField=AI_LLM", "sortBy=SCORE", "1. 7 t");
    }

    @Test
    @DisplayName("기억 없이 만든 ChatTurn 은 사건만 보내고 아무것도 기록하지 않는다")
    void withoutMemory() {
        ChatTurn turn = new ChatTurn();

        turn.publish(SearchContext.query("q", null, null, null), EVIDENCE);
        turn.publishStories(null, "SCORE", List.of());
        List<ChatEvent> events = turn.stream(Flux.empty()).collectList().block();

        assertThat(events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Plans", "Evidence", "Completed");
    }

    @Test
    @DisplayName("검색이 없는 턴은 token 과 done 만 흐른다")
    void withoutSearch() {
        ChatTurn turn = new ChatTurn();

        List<ChatEvent> events = turn.stream(Flux.just("3건입니다")).collectList().block();

        assertThat(events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Token", "Completed");
    }

    @Test
    @DisplayName("Advisor 가 남긴 근거를 첫 조각의 컨텍스트에서 꺼내 token 보다 먼저 보낸다")
    void streamsWithEvidence() {
        List<Document> evidence = EvidenceDocuments.evidence(EvidenceDocumentsTest.CANDIDATES,
                List.of(new RelevantStory(1L, null)), EvidenceDocumentsTest.DETAILS, Map.of());
        Map<String, Object> context = new HashMap<>();
        context.put(SearchContext.PLAN_SUMMARIES, List.of(new PlanSummary(1, "키워드 완전 일치", 0, "keywords=[], suitable")));
        context.put(SearchContext.MERGED, 2);
        context.put(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, evidence);

        Flux<ChatClientResponse> responses = Flux.just("", "안", "녕")
                .map(text -> ChatClientResponse.builder()
                        .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))))
                        .context(context)
                        .build());

        List<ChatEvent> events = new ChatTurn().streamWithEvidence(responses).collectList().block();

        assertThat(events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Plans", "Evidence", "Token", "Token", "Completed");
        assertThat(((ChatEvent.Plans) events.get(0)).merged()).isEqualTo(2);
        assertThat(((ChatEvent.Evidence) events.get(1)).items()).extracting(ChatEvent.EvidenceItem::storyId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("근거가 없는 응답 흐름은 token 과 done 만 흐른다")
    void streamsWithoutEvidence() {
        Flux<ChatClientResponse> responses = Flux.just(ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("3건입니다")))))
                .context(Map.of())
                .build());

        List<ChatEvent> events = new ChatTurn().streamWithEvidence(responses).collectList().block();

        assertThat(events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Token", "Completed");
    }
}
