package hn.chatbot.ai;

/**
 * Q&A 답변 규칙을 제공한다. ChatService 가 system 메시지로 넣는다.
 *
 * 답은 근거를 받은 모델이 생성한다. ChatService 1단계에서는 RetrievalAugmentationAdvisor 가
 * 근거를 질문에 붙여 넘기고, 2단계에서는 searchIssues 도구의 결과로 받는다. 어느 쪽이든
 * 근거는 같은 모양의 JSON(SearchEvidence)이다. 그 모델이 따를 규칙이 여기 있다.
 *
 * 기본 규칙
 *
 * - 주어진 근거만으로 답한다. 근거에 없는 내용은 모른다고 한다
 * - 원문 타입(category)에 따라 서술 강도를 달리한다. 공식 발표와 개인 의견을 구분한다
 * - 커뮤니티 반응과 실무 시사점을 답에 넣는다
 * - 어떤 내용이 몇 번 근거에서 나왔는지 답에 표시한다. 사용자가 출처를 대조할 수 있어야 한다.
 *   그 번호가 SearchEvidence.Item.rank 이고 화면의 근거 카드 순서와 같다
 * - 질문과 같은 언어로 답한다
 *
 * 2단계 규칙 (ChatService 2단계에서 더한다)
 *
 * 모델이 도구로 검색 조건을 정하고 대화 기억이 붙으면서 생기는 문제를 막는다.
 *
 * - 검색 조건을 해석한다. 필터를 걸어 결과가 없으면 필터를 풀지 말고 그 사실을 말한다
 * - 대화 기억에 남은 기록([search record] 등)의 형식을 답에 옮겨 쓰지 않는다
 */
public interface AnswerGenerator {

    String answerRules();
}
