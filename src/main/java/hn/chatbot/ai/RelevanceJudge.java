package hn.chatbot.ai;

import java.util.List;

/**
 * 후보 중 질의에 실제로 답이 되는 것을 고르고, 근거 대목을 뽑는다.
 *
 * 입력은 후보별 제목 · 요약 · 원문 앞부분(excerpt)이다. 요약만 보지 않고 원문도 읽어
 * 판단하고, 읽은 김에 질의와 관련된 대목을 원문 그대로 뽑는다.
 *
 * - 최종 max 건, 관련 있는 것이 적으면 최소 1건. 돌려준 순서가 곧 근거 카드 순서다
 * - 받은 후보의 storyId 만 돌려준다. 모델이 목록에 없는 id 를 만들어 낼 수 있다
 * - passage 는 원문을 바꿔 쓰지 않은 문장, 300자 이내. 없으면 null
 *
 * 위 세 가지는 프롬프트로 지시하는 것으로 끝내지 않는다. 모델이 어기고 돌려줄 수 있으므로
 * 돌려주기 전에 코드로 되받는다. 걸러 낸 뒤 남은 것이 없으면 첫 후보를 대목 없이 1건 돌려준다.
 *
 * 계획별 점수를 합치지 않고 이 판단이 최종 순위를 정한다.
 * 불리언 · ts_rank · 코사인 유사도는 서로 비교할 수 없는 척도이기 때문이다.
 */
public interface RelevanceJudge {

    List<RelevantStory> selectRelevant(String question, List<AnalysisTarget> targets, int max);
}
