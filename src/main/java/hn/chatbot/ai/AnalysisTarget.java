package hn.chatbot.ai;

/**
 * 판단 · 요약에 넘기는 이슈 하나. ai/ 가 소유하는 입력 모델이다.
 *
 * 쓰는 곳마다 필요한 필드가 달라 생성 메서드를 둘로 나눴다. 쓰지 않는 필드는 null 이다.
 *
 * - forJudge: 적합성 판단. 제목 · 요약 · 원문 앞부분(excerpt, 최대 2,000자)
 * - forTopic: 분야 요약. 제목 · 요약 · 커뮤니티 반응
 *
 * 검색이 찾아낸 Candidate 를 그대로 받지 않는다. matchedPlans 처럼 검색 사정으로
 * 생긴 값은 LLM 이 쓸 일이 없고, 그걸 받으면 ai/ 가 검색 계층에 묶인다.
 * 옮기는 것은 부르는 쪽(RelevancePostProcessor · TopicService)의 몫이다.
 */
public record AnalysisTarget(long storyId, String title, String summary,
                             String communityReaction, String excerpt) {

    public static AnalysisTarget forJudge(long storyId, String title, String summary, String excerpt) {
        return new AnalysisTarget(storyId, title, summary, null, excerpt);
    }

    public static AnalysisTarget forTopic(long storyId, String title, String summary, String communityReaction) {
        return new AnalysisTarget(storyId, title, summary, communityReaction, null);
    }
}
