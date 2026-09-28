package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import reactor.core.publisher.Flux;

/**
 * 질문 하나를 받아 답변을 스트리밍한다.
 *
 * 구현은 셋이다. 단계마다 하나씩 채운다. 단계별 구성은 각 구현의 Javadoc 에 있다.
 * ChatController 는 @Primary 가 붙은 구현을 주입받는다. 새 단계를 채우면 @Primary 를 그 구현으로 옮긴다.
 *
 * - 1단계 RagChatServiceShell: RetrievalAugmentationAdvisor 로 질문마다 검색하는 RAG
 * - 2단계 AgentChatServiceShell: 도구(StoryTools · TopicTools)를 쓰는 에이전트
 * - 3단계 MemoryChatServiceShell: AgentChatServiceShell 을 상속해 대화 기억을 더한 에이전트
 *
 * 공통
 *
 * - 적재된 스토리가 0건이면(TopicService.count(null)) 모델을 부르지 않는다.
 *   안내 문구를 Token 으로 흘리고 Completed 로 닫는다
 * - 사건의 순서는 plans → evidence → token 반복 → done 이다. 화면이 이 순서대로 그린다.
 *   사건을 만드는 통로는 완성본 ChatTurn 이다
 * - conversationId 는 UUID 다. 프론트가 대화를 시작할 때 만들어 보낸다
 *
 * SseEmitter 를 반환하지 않는다. 전송 방식은 웹 계층의 관심사다.
 * 여기서는 사건만 흘리고 컨트롤러가 구독해 SSE 로 옮긴다.
 */
public interface ChatService {

    Flux<ChatEvent> chat(String conversationId, String question);
}
