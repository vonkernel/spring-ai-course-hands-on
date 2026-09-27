package hn.chatbot.ai;

/**
 * 파이프라인 3단계 — ModerationModel 로 댓글의 카테고리별 점수를 받는다.
 *
 * 판정은 isFlagged() 가 아니라 ModerationProperties.categories() 에 적힌 카테고리의
 * 점수만 임계값과 비교해 낸다.
 *
 * 그 목록은 설정값이라 바뀔 수 있고, 모델이 채우지 않는 이름이 들어 있을 수 있다.
 * 점수를 찾지 못한 카테고리를 그대로 비교하면 판정 자체가 실패한다. 없는 점수는 0 으로 본다.
 *
 * scores 의 키는 OpenAI 카테고리 이름을 쓴다(sexual, sexual/minors, self-harm/intent 처럼
 * 슬래시 · 하이픈 표기). application.yml 의 categories 와 같은 표기라야 비교가 맞는다.
 * topCategory · topScore 는 관찰용이라 판정 대상이 아닌 카테고리까지 포함한 최고점이다.
 */
public interface CommentModerator {

    ModerationVerdict inspect(String text);
}
