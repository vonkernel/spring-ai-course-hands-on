package hn.chatbot.ai;

import java.util.List;

/**
 * 파이프라인 6단계 — 구조화 출력으로 분석 결과를 받는다. 적합성 판정을 포함한다.
 *
 * techField 와 category 는 아래 목록에서 하나를 고르게 프롬프트에 지시한다.
 *
 * techField: AI_LLM · SECURITY_PRIVACY · OPEN_SOURCE · INFRASTRUCTURE_ENTERPRISE · PLATFORM_POLICY ·
 *            DEV_CULTURE_PRACTICE · HARDWARE · MOBILITY · NON_TECHNICAL
 * category:  OFFICIAL_ANNOUNCEMENT · RELEASE_NOTES · NEWS_REPORT · OPINION_ESSAY · TECHNICAL_DEEP_DIVE ·
 *            RESEARCH_PAPER · SHOW_HN_PROJECT · ASK_TELL_HN · PRODUCT_MARKETING
 *
 * 폴백: 목록 어디에도 맞지 않으면 목록과 같은 표기(대문자와 밑줄, 예: QUANTUM_COMPUTING)로
 * 새 값을 만들게 한다. 비기술 주제는 새 값을 만들지 않고 techField 를 NON_TECHNICAL 로 둔다.
 *
 * 목록을 빠뜨리면 모델이 표기부터 제각각인 값을 만든다(예: Firmware failure).
 * 폴백은 목록을 보여준 뒤 어디에도 맞지 않을 때를 위한 것이다.
 *
 * 서술 필드는 한국어로 쓰게 프롬프트에 적는다. 원문이 영어여도 마찬가지다.
 * 적지 않으면 원문 언어를 따라가 스토리마다 언어가 섞인다. 언어별 예외는 IssueAnalysis 에 있다.
 */
public interface IssueAnalyzer {

    IssueAnalysis analyze(String title, String body, List<String> topComments);
}
