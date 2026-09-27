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

    private final ChatClient chatClient;

    public IssueAnalyzerShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public IssueAnalysis analyze(String title, String body, List<String> topComments) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
