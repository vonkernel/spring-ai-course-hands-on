package hn.chatbot.service.chat;

import hn.chatbot.search.SearchContext;
import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.model.StorySummary;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Q&A 한 턴 동안 화면으로 보낼 사건을 모으고, 도구 결과를 대화 기억에 기록하는 통로. 완성본이다.
 *
 * 사건은 plans → evidence → token 반복 → done 순서로 흐른다. 검색 결과(plans · evidence)가
 * 어디서 나오느냐는 ChatService 구현마다 다르다.
 *
 * 1단계 RagChatServiceShell: RetrievalAugmentationAdvisor 가 검색한다. 검색 결과는 Advisor 가 요청 컨텍스트에
 * 남기고, Spring AI 가 그 컨텍스트를 모든 응답 조각에 복사한다. streamWithEvidence 가 첫 조각의
 * 컨텍스트에서 꺼내 방출한다.
 *
 *   ChatService : return new ChatTurn().streamWithEvidence(chatClient.prompt()...stream().chatClientResponse());
 *
 * 2단계 AgentChatServiceShell: searchIssues 도구가 검색한다. 도구의 반환값은 모델에게 가므로 ChatService 가 사건을
 * 받을 다른 길이 없다. 이 객체를 toolContext 에 넣어 건넨다.
 *
 *   ChatService : ChatTurn turn = new ChatTurn();
 *                 .toolContext(Map.of(ChatTurn.KEY, turn, ...))
 *                 return turn.stream(모델의 token 흐름);
 *   searchIssues: ChatTurn.from(ctx).publish(search, evidence);
 *   listStories : ChatTurn.from(ctx).publishStories(techField, sortBy, stories);
 *
 * 대화 기억: 도구 호출의 중간 메시지는 기억 Advisor 가 저장하지 않는다. 후속 질문에 필요한 번호와
 * storyId 는 도구 결과에만 있으므로, 기억을 받아 만든 ChatTurn 은 도구가 넘긴 결과를 SearchRecord 문구로
 * 기록한다. 기억 없이 만들면 화면 사건만 보낸다.
 *
 *   MemoryChatServiceShell : ChatTurn turn = new ChatTurn(chatMemory, conversationId);
 *
 * 기록은 도구 실행 중에 일어나므로 기억에는 질문 → 기록 → 답변 순서로 남는다.
 *
 * 턴마다 새로 만든다. ThreadLocal 이 아니므로 비울 책임이 없다.
 * 순서는 자연히 보장된다. 두 경우 모두 검색이 끝난 뒤에 모델이 답을 생성하므로
 * publish 한 사건이 첫 token 보다 앞선다.
 */
public final class ChatTurn {

    public static final String KEY = "chatTurn";

    private final Sinks.Many<ChatEvent> events = Sinks.many().unicast().onBackpressureBuffer();
    private final ChatMemory chatMemory;
    private final String conversationId;

    /** 화면 사건만 보낸다. */
    public ChatTurn() {
        this(null, null);
    }

    /** 화면 사건을 보내고, 도구가 넘긴 결과를 conversationId 의 대화 기억에 기록한다. */
    public ChatTurn(ChatMemory chatMemory, String conversationId) {
        this.chatMemory = chatMemory;
        this.conversationId = conversationId;
    }

    public static ChatTurn from(ToolContext ctx) {
        return (ChatTurn) ctx.getContext().get(KEY);
    }

    /**
     * searchIssues 의 결과를 받는다. plans · evidence 사건으로 방출하고,
     * 기억이 있으면 검색 조건과 근거 목록을 SearchRecord.of 문구로 기록한다.
     */
    public void publish(Query search, SearchEvidence evidence) {
        emit(evidence);
        remember(SearchRecord.of(search.text(), SearchContext.techField(search), SearchContext.category(search),
                evidence));
    }

    /** listStories 의 결과를 받는다. 기억이 있으면 조건과 목록 순서를 SearchRecord.ofStories 문구로 기록한다. */
    public void publishStories(String techField, String sortBy, List<StorySummary> stories) {
        remember(SearchRecord.ofStories(techField, sortBy, stories));
    }

    private void remember(String record) {
        if (chatMemory != null) {
            chatMemory.add(conversationId, new AssistantMessage(record));
        }
    }

    /** 검색 결과를 plans · evidence 사건으로 방출한다. */
    private void emit(SearchEvidence evidence) {
        events.tryEmitNext(new ChatEvent.Plans(evidence.plans().stream()
                .map(p -> new ChatEvent.PlanOutcome(p.plan(), p.name(), p.hits(), p.condition()))
                .toList(), evidence.merged()));
        events.tryEmitNext(new ChatEvent.Evidence(evidence.evidence().size(), evidence.evidence().stream()
                .map(i -> new ChatEvent.EvidenceItem(i.storyId(), i.title(), i.url(), i.matchedPlans(),
                        i.category(), i.summary(), i.communityReaction(), i.practicalImplication(),
                        i.passage()))
                .toList()));
    }

    /** 도구가 방출한 사건과 모델의 token 을 합쳐 화면으로 보낼 흐름을 만든다. 끝에 done 을 붙인다. */
    public Flux<ChatEvent> stream(Flux<String> tokens) {
        Flux<ChatEvent> tokenEvents = tokens
                .<ChatEvent>map(ChatEvent.Token::new)
                .doFinally(signal -> events.tryEmitComplete());
        return Flux.merge(events.asFlux(), tokenEvents)
                .concatWith(Flux.just(new ChatEvent.Completed("STOP")));
    }

    /**
     * RetrievalAugmentationAdvisor 를 붙인 호출의 응답 흐름에서 사건을 만든다.
     *
     * 첫 조각의 컨텍스트에서 계획별 요약과 근거(DOCUMENT_CONTEXT)를 꺼내 plans · evidence 로
     * 방출하고, 조각마다의 텍스트는 token 으로 흘린다. 끝에 done 을 붙인다.
     * Advisor 가 근거를 남기지 않은 호출이면 token 과 done 만 흐른다.
     */
    public Flux<ChatEvent> streamWithEvidence(Flux<ChatClientResponse> responses) {
        AtomicBoolean first = new AtomicBoolean(true);
        Flux<String> tokens = responses
                .doOnNext(response -> {
                    if (first.getAndSet(false)) {
                        publishFrom(response.context());
                    }
                })
                .mapNotNull(ChatTurn::text)
                .filter(text -> !text.isEmpty());
        return stream(tokens);
    }

    @SuppressWarnings("unchecked")
    private void publishFrom(Map<String, Object> context) {
        Object documents = context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (documents instanceof List<?> list) {
            emit(SearchEvidence.of(context, (List<Document>) list));
        }
    }

    private static String text(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }
}
