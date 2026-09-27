package hn.chatbot.ai.shell;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.TopicSummarizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기술 분야 하나의 논의 흐름을 산문으로 요약한다. 스토리마다 제목 · 요약 · 커뮤니티 반응이 들어온다.
 *
 * ChatClient 는 주입돼 있다.
 */
@Component
public class TopicSummarizerShell implements TopicSummarizer {

    private final ChatClient chatClient;

    public TopicSummarizerShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String summarize(String techField, List<AnalysisTarget> targets) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
