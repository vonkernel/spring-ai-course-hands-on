package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.TopicService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * Q&A 2단계: 도구를 쓰는 에이전트.
 *
 * RetrievalAugmentationAdvisor 를 쓰지 않는다. StoryTools 와 TopicTools 를 도구로 등록하고, 검색은 모델이 searchStories 를
 * 골랐을 때만 일어난다. 답변 지시는 한국어 system 프롬프트 상수로 둔다.
 *
 * 흐름
 *
 * 1 ChatTurn 을 newTurn(conversationId) 로 만든다
 * 2 system 은 instructions(), user 는 질문으로 하고 StoryTools · TopicTools 를 도구로 등록한다.
 *   toolContext 에 ChatTurn(ChatTurn.KEY), 질문 원문(StoryTools.USER_QUESTION)을 넣는다
 * 3 그 요청을 advise(spec, conversationId) 에 통과시킨다
 * 4 stream().content() 를 ChatTurn.stream 에 넘겨 돌려준다
 *
 * instructions · newTurn · advise 는 이 클래스를 상속하는 MemoryChatServiceShell 이 재정의한다.
 * 이 클래스의 instructions 는 답변 지시 상수를 돌려준다. newTurn 은 new ChatTurn() 을,
 * advise 는 받은 요청을 그대로 돌려준다.
 *
 * 답변 지시: RagChatServiceShell 의 지시를 바탕으로 근거 번호 항목을 바꾸고 다음을 더한다
 *
 * - 근거 번호 항목: searchStories 결과의 근거를 쓸 때만 그 근거의 rank 를 [1] 처럼 표시한다.
 *   번호는 화면의 근거 카드 번호와 같다. 목록 · 건수 · 요약 결과는 번호 없이 제목으로 가리킨다
 * - 검색 조건을 해석한다. 필터를 걸어 결과가 없으면 필터를 풀지 말고 그 사실을 말한다
 *
 * @Service 와 @Primary 를 붙여 ChatController 가 RagChatServiceShell 대신 이 구현을 주입받게 한다.
 * 붙이기 전에는 빈(Bean)으로 등록되지 않으므로 Q&A 탭은 RagChatServiceShell 로 답한다.
 *
 * ChatClient.Builder, StoryTools, TopicTools, TopicService 가 주입돼 있다.
 */
@Service
@Primary
public class AgentChatServiceShell implements ChatService {

    private static final String NO_DATA_MESSAGE =
            "아직 적재된 이슈가 없습니다. 데이터 셋업 탭에서 먼저 셋업을 실행해 주세요.";

    private static final String ANSWER_INSTRUCTIONS = """
            너는 Hacker News 기술 이슈를 바탕으로 질문에 답하는 어시스턴트다.

            - 주어진 근거(evidence 목록)에 있는 내용만으로 답한다. 근거에 없는 내용은
              지어내지 말고 모른다고 말한다.
            - 원문 타입(category)에 따라 서술 강도를 달리한다. 공식 발표(OFFICIAL_ANNOUNCEMENT,
              RELEASE_NOTES)는 사실로 전달하고, 개인 의견(OPINION_ESSAY)이나 커뮤니티 질문
              (ASK_TELL_HN) 은 한 사람의 시각임을 분명히 한다.
            - 근거에 커뮤니티 반응(communityReaction)이나 실무 시사점(practicalImplication)이
              있으면 답에 포함한다.
            - searchStories 결과의 근거를 쓸 때만 그 근거의 rank 를 [1] 처럼 표시한다. 사용자가 출처를
              대조할 수 있어야 한다. 번호는 화면의 근거 카드 번호와 같다. 목록 · 건수 · 요약 결과는
              번호 없이 제목으로 가리킨다.
            - 질문과 같은 언어로 답한다.
            - 도구 결과의 plans 에 있는 검색 조건(condition)을 확인해 어떤 조건으로 찾았는지 답에
              반영한다. 필터를 걸어서 결과가 적거나 없으면 필터를 풀어서 다시 찾지 말고, 그 조건으로는
              결과가 없었다는 사실을 그대로 말한다.
            """;

    private final ChatClient chatClient;
    private final StoryTools storyTools;
    private final TopicTools topicTools;
    private final TopicService topicService;

    public AgentChatServiceShell(ChatClient.Builder builder, StoryTools storyTools, TopicTools topicTools,
                                 TopicService topicService) {
        this.chatClient = builder.build();
        this.storyTools = storyTools;
        this.topicTools = topicTools;
        this.topicService = topicService;
    }

    @Override
    public Flux<ChatEvent> chat(String conversationId, String question) {
        if (topicService.count(null) == 0) {
            return guidance(NO_DATA_MESSAGE);
        }

        ChatTurn turn = newTurn(conversationId);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(instructions())
                .user(question)
                .tools(storyTools, topicTools)
                .toolContext(Map.of(ChatTurn.KEY, turn, StoryTools.USER_QUESTION, question));

        Flux<String> tokens = advise(spec, conversationId)
                .stream()
                .content();

        return turn.stream(tokens);
    }

    protected String instructions() {
        return ANSWER_INSTRUCTIONS;
    }

    protected ChatTurn newTurn(String conversationId) {
        return new ChatTurn();
    }

    protected ChatClient.ChatClientRequestSpec advise(ChatClient.ChatClientRequestSpec spec, String conversationId) {
        return spec;
    }

    private static Flux<ChatEvent> guidance(String message) {
        Flux<ChatEvent> tokens = Flux.fromArray(message.split(""))
                .delayElements(Duration.ofMillis(40))
                .map(ChatEvent.Token::new)
                .cast(ChatEvent.class);

        return tokens.concatWith(Flux.just(new ChatEvent.Completed("STOP")));
    }
}
