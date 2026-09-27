package hn.chatbot.ai.shell;

import hn.chatbot.ai.IssueAnalysis;
import hn.chatbot.ai.IssueAnalyzer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 6단계 — 구조화 출력으로 분석 결과를 받는다. techField 와 category 의 값 목록을 프롬프트에 반드시 넣는다.
 *
 * ChatClient 는 주입돼 있다.
 */
@Component
public class IssueAnalyzerShell implements IssueAnalyzer {

    private static final String SYSTEM = """
            너는 Hacker News 이슈 하나(제목 · 본문 · 최상위 댓글)를 읽고 구조화된 분석 결과를
            만든다.

            category 값은 다음 중 하나를 고른다. 어디에도 맞지 않으면 이 목록과 같은 표기 방식
            (대문자와 밑줄)으로 새 값을 만든다.
            OFFICIAL_ANNOUNCEMENT, RELEASE_NOTES, NEWS_REPORT, OPINION_ESSAY, TECHNICAL_DEEP_DIVE,
            RESEARCH_PAPER, SHOW_HN_PROJECT, ASK_TELL_HN, PRODUCT_MARKETING

            techField 값은 다음 중 하나를 고른다. 어디에도 맞지 않으면 같은 표기 방식으로
            새 값을 만든다.
            AI_LLM, SECURITY_PRIVACY, OPEN_SOURCE, INFRASTRUCTURE_ENTERPRISE, PLATFORM_POLICY,
            DEV_CULTURE_PRACTICE, HARDWARE, MOBILITY, NON_TECHNICAL

            나머지 필드
            - summary: 본문을 2~3문장으로 요약한다.
            - keywords: 핵심 키워드 6~10개. 고유명사 · 제품명 · 기술 이름은 원문 표기 그대로 쓰고
              (예: VMware, llama.cpp), 일반 개념어는 한국어로 쓴다.
            - communityReaction: 최상위 댓글들이 보이는 반응의 경향을 요약한다. 댓글이 없으면
              반응이 없다는 취지로 짧게 적는다.
            - practicalImplication: 실무자에게 어떤 의미가 있는지 한두 문장으로 적는다.

            언어
            - summary · communityReaction · practicalImplication · unsuitableReason 은 원문이
              영어여도 한국어로 쓴다.
            - category · techField 는 위 목록의 표기(대문자와 밑줄) 그대로 쓴다.

            적합성 판정 (suitable)
            - 광고 · 스팸에 불과하거나, 본문에 실질적인 내용이 없거나(잘린 텍스트, 알아볼 수
              없는 문자열 등), 분석할 만한 주제나 논의를 찾을 수 없는 경우에만 suitable=false
              로 판정하고 unsuitableReason 에 그 사유를 한 문장으로 적는다.
            - 그 외에는 (비기술적인 주제라도 techField 를 NON_TECHNICAL 로 두고) suitable=true
              로 판정하고 unsuitableReason 은 빈 문자열로 둔다.
            """;

    private final ChatClient chatClient;

    public IssueAnalyzerShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public IssueAnalysis analyze(String title, String body, List<String> topComments) {
        return chatClient.prompt()
                .system(SYSTEM)
                .user("제목: %s\n\n본문:\n%s\n\n최상위 댓글:\n%s".formatted(
                        title, body, String.join("\n---\n", topComments)))
                .call()
                .entity(IssueAnalysis.class);
    }
}
