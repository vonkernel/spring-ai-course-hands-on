package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import reactor.core.publisher.Flux;

/**
 * 질문 하나를 받아 답변을 스트리밍한다. 구현은 두 단계로 채운다.
 * 세션 5 에서 질문마다 검색하는 RAG 로 만들고, 세션 6 에서 검색을 도구로 옮겨 에이전트로 바꾼다.
 *
 * 공통
 *
 * 적재된 스토리가 0건이면(TopicService.count(null, null)) 모델을 부르지 않는다.
 * 안내 문구를 Token 으로 흘리고 Completed 로 닫는다. 빈 결과에서 답을 생성하지 않게 막는 분기다.
 *
 * 사건의 순서는 plans → evidence → token 반복 → done 이다. 화면이 이 순서대로 점진적으로 그린다.
 *
 * 1단계 — 세션 5: 질문마다 검색하는 RAG
 *
 * ChatClient 에 붙여 stream() 으로 호출
 *   system  : AnswerGenerator.answerRules()
 *   user    : 질문
 *   advisors: RetrievalAugmentationAdvisor
 *               documentRetriever      : SearchService
 *               documentPostProcessors : RelevancePostProcessor
 *               queryAugmenter         : ContextualQueryAugmenter, documentFormatter 는 SearchEvidence::format
 *   ↓
 * stream().chatClientResponse() 를 new ChatTurn().streamWithEvidence(...) 에 넘겨 돌려준다
 *
 * Advisor 가 모델 호출 앞에서 검색 → 적합성 판단 → 질문 보강을 한다. 검색 결과는 Advisor 가
 * 요청 컨텍스트에 남기고, ChatTurn 이 첫 응답 조각의 컨텍스트에서 꺼내 plans · evidence 로 보낸다.
 * 대화 기억은 없다. 질문 하나가 한 턴으로 끝난다.
 *
 * 2단계 — 세션 6: 도구와 기억을 쓰는 에이전트
 *
 * 1단계의 RetrievalAugmentationAdvisor 를 빼고, 같은 부품을 부르는 searchIssues 도구를 등록한다.
 *
 * ChatTurn 을 새로 만든다
 *   ↓
 * ChatClient 에 붙여 stream() 으로 호출
 *   system     : AnswerGenerator.answerRules()
 *   user       : 질문
 *   tools      : issueTools
 *   toolContext: conversationId · ChatTurn · 질문 원문
 *                (키: ChatMemory.CONVERSATION_ID, ChatTurn.KEY, SearchContext.USER_QUESTION)
 *   advisors   : 주입된 ChatMemory 로 만든 MessageChatMemoryAdvisor 와 conversationId 파라미터
 *   ↓
 * 모델이 필요하면 searchIssues 를 호출한다. 검색 · 적합성 판단 · 사건 방출 · 기억 기록은 도구가 한다
 *   ↓
 * turn.stream(모델의 token 흐름) 을 돌려준다
 *
 * 검색이 없는 질문(건수 · 댓글 · 분포 · 분야 요약)은 token 과 done 만 흐른다.
 *
 * 질문 원문을 toolContext 에 넣는 이유: searchIssues 의 query 인자는 모델이 다듬은 값이라
 * 사용자가 쓴 키워드가 그대로 남지 않는다. 계획 1 은 원문에서 키워드를 찾는다.
 *
 * 대화 맥락 (2단계)
 *
 * conversationId 는 필수다. ChatMemory.CONVERSATION_ID 에 기본값이 없고,
 * DB 컬럼이 VARCHAR(36) 이라 UUID 여야 한다. 프론트가 세션 시작 시 만들어 보낸다.
 *
 * 같은 conversationId 를 advisors 파라미터와 toolContext 에 각각 넣는다. 하나는 기억 Advisor 가,
 * 다른 하나는 도구 본문이 읽는다. 서로 다른 통로다.
 *
 * SseEmitter 를 반환하지 않는다
 *
 * 전송 방식은 웹 계층의 관심사다. 여기서는 사건만 흘리고 컨트롤러가 구독해 SSE 로 옮긴다.
 */
public interface ChatService {

    Flux<ChatEvent> chat(String conversationId, String question);
}
