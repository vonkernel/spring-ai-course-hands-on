package hn.chatbot.ai;

import java.util.List;

/**
 * 6단계 LLM 분석의 구조화 출력. analysis 테이블 컬럼과 대응한다.
 *
 * storyId 는 없다. 이 레코드에서 JSON 스키마가 만들어져 프롬프트에 붙으므로,
 * 필드가 있으면 모델이 알 수 없는 스토리 번호를 만들어 내야 한다.
 * 저장할 때 AnalysisMapper.toEntity(storyId, analysis) 가 붙인다.
 *
 * suitable 과 unsuitableReason 은 분석과 동시에 채워지므로 별도 API 호출이 없다.
 *
 * keywords 는 6개에서 10개를 받는다. 검색 계획 1(KeywordArrayPlan)이 사용자 질문 안에 이 배열의
 * 키워드가 들어 있는지 보므로, 적게 뽑으면 그 계획이 0건 나는 빈도가 올라간다.
 *
 * 서술 필드(summary · communityReaction · practicalImplication · unsuitableReason)는 한국어로 받는다.
 * keywords 의 고유명사 · 제품명은 원문 표기 그대로 둔다(VMware, llama.cpp). 사용자가 쓴 표기로
 * 계획 1 이 찾기 때문이다. techField · category 는 허용값 목록의 표기 그대로다.
 *
 * 구조화 출력은 값이 없는 String 필드에 null 이 아니라 빈 문자열을 채워 돌려준다.
 * analysis 에는 CHECK (suitable = (unsuitable_reason IS NULL)) 가 걸려 있어
 * 그대로 저장하면 INSERT 가 실패한다. 정규화는 AnalysisMapper 가 맡는다.
 */
public record IssueAnalysis(
        String summary,
        String category,
        String techField,
        List<String> keywords,
        String communityReaction,
        String practicalImplication,
        boolean suitable,
        String unsuitableReason) {
}
