package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import reactor.core.publisher.Flux;

/**
 * Q&A 2단계: 도구와 대화 기억을 쓰는 에이전트.
 *
 * RetrievalAugmentationAdvisor 를 쓰지 않는다. IssueTools 를 도구로 등록하고 MessageChatMemoryAdvisor 를
 * 붙인다. 검색은 모델이 searchIssues 를 골랐을 때만 일어난다. 답변 규칙은 system 프롬프트로 넣는다.
 * 구성은 실습 프롬프트로 전달한다.
 *
 * 답변 규칙: RagChatServiceShell 의 규칙에 다음을 더한다
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
