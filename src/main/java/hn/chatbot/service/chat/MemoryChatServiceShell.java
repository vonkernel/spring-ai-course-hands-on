package hn.chatbot.service.chat;

import hn.chatbot.service.topic.TopicService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Q&A 3단계: 2단계 에이전트에 대화 기억을 더한다.
 *
 * AgentChatServiceShell 을 상속하고 instructions · newTurn · advise 만 재정의한다.
 * 도구 등록과 흐름은 부모의 chat 을 그대로 쓴다.
 *
 * - instructions: 부모의 답변 지시에 다음을 더한다.
 *   대화 기억에 남은 기록([search record] 등)의 형식을 답에 옮겨 쓰지 않는다
 * - newTurn: new ChatTurn(chatMemory, conversationId) 를 돌려준다. 도구가 넘긴 결과를 ChatTurn 이 기억에 기록한다
 * - advise: MessageChatMemoryAdvisor 를 붙이고 conversationId 를 Advisor 파라미터(ChatMemory.CONVERSATION_ID)로 넘긴다
 *
 * 기억 Advisor 는 도구 호출의 중간 메시지를 저장하지 않는다. 후속 질문에 필요한 번호와 storyId 는
 * ChatTurn 이 남긴 기록으로 이어진다.
 *
 * @Service 와 @Primary 를 붙이고, AgentChatServiceShell 의 @Primary 는 뗀다. @Primary 는 상속되지 않고,
 * 두 구현에 모두 붙어 있으면 primary 빈이 둘이라 애플리케이션이 뜨지 않는다.
 *
 * ChatMemory 가 주입돼 있다.
 */
public class MemoryChatServiceShell extends AgentChatServiceShell {

    private final ChatMemory chatMemory;

    public MemoryChatServiceShell(ChatClient.Builder builder, StoryTools storyTools, TopicTools topicTools,
                                  TopicService topicService, ChatMemory chatMemory) {
        super(builder, storyTools, topicTools, topicService);
        this.chatMemory = chatMemory;
    }

    @Override
    protected String instructions() {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    @Override
    protected ChatTurn newTurn(String conversationId) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    @Override
    protected ChatClient.ChatClientRequestSpec advise(ChatClient.ChatClientRequestSpec spec, String conversationId) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
