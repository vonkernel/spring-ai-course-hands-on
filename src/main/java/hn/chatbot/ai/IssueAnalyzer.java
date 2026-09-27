package hn.chatbot.ai;

import java.util.List;

/**
 * 파이프라인 6단계 — 구조화 출력으로 분석 결과를 받는다. 적합성 판정을 포함한다.
 *
 * techField 와 category 의 값 목록을 프롬프트에 반드시 넣는다.
 * 목록을 빠뜨리면 모델이 목록에 없는 값을 만들어낸다. 폴백 규칙은 목록을 보여준 뒤
 * 어디에도 해당하지 않을 때를 위한 것이다.
 *
 * 서술 필드는 한국어로 쓰게 프롬프트에 적는다. 원문이 영어여도 마찬가지다.
 * 적지 않으면 원문 언어를 따라가 스토리마다 언어가 섞인다. 언어별 예외는 IssueAnalysis 에 있다.
 */
public interface IssueAnalyzer {

    IssueAnalysis analyze(String title, String body, List<String> topComments);
}
