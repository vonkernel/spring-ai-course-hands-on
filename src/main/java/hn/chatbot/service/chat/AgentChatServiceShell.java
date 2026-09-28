package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.TopicService;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

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
public class AgentChatServiceShell implements ChatService {

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
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    protected String instructions() {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    protected ChatTurn newTurn(String conversationId) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    protected ChatClient.ChatClientRequestSpec advise(ChatClient.ChatClientRequestSpec spec, String conversationId) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
