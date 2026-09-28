package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import reactor.core.publisher.Flux;

/**
 * Q&A 2단계: 도구와 대화 기억을 쓰는 에이전트.
 *
 * RetrievalAugmentationAdvisor 를 쓰지 않는다. IssueTools 를 도구로 등록하고 MessageChatMemoryAdvisor 를
 * 붙인다. 검색은 모델이 searchIssues 를 골랐을 때만 일어난다. 답변 지시는 한국어 system 프롬프트 상수로 둔다.
 *
 * 흐름
 *
 * 1 IssueTools 를 도구로 등록하고, toolContext 에 conversationId(ChatMemory.CONVERSATION_ID),
 *   ChatTurn(ChatTurn.KEY), 질문 원문(IssueTools.USER_QUESTION)을 넣는다
 * 2 MessageChatMemoryAdvisor 를 붙이고 conversationId 를 Advisor 파라미터(ChatMemory.CONVERSATION_ID)로 넘긴다
 * 3 system 은 답변 지시 상수, user 는 질문이다
 * 4 stream().content() 를 ChatTurn.stream 에 넘겨 돌려준다
 *
 * 답변 지시: RagChatServiceShell 의 지시에 다음을 더한다
 *
 * - 검색 조건을 해석한다. 필터를 걸어 결과가 없으면 필터를 풀지 말고 그 사실을 말한다
 * - 대화 기억에 남은 기록([search record] 등)의 형식을 답에 옮겨 쓰지 않는다
 *
 * @Service 와 @Primary 를 붙여 ChatController 가 RagChatServiceShell 대신 이 구현을 주입받게 한다.
 * 붙이기 전에는 빈(Bean)으로 등록되지 않으므로 Q&A 탭은 RagChatServiceShell 로 답한다.
 */
public class AgentChatServiceShell implements ChatService {

    @Override
    public Flux<ChatEvent> chat(String conversationId, String question) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
