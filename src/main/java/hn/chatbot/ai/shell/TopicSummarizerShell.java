package hn.chatbot.ai.shell;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.TopicSummarizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 기술 분야 하나의 논의 흐름을 산문으로 요약한다. 스토리마다 제목 · 요약 · 커뮤니티 반응이 들어온다.
 *
 * ChatClient 는 주입돼 있다.
 */
@Component
public class TopicSummarizerShell implements TopicSummarizer {

    private static final String SYSTEM = """
            너는 특정 기술 분야 안에서 여러 이슈에 걸쳐 나타나는 논의 흐름을 산문으로
            요약하는 역할을 한다.

            입력으로 그 분야에 속한 스토리 여러 건의 제목 · 요약 · 커뮤니티 반응을 받는다.

            규칙
            - 스토리를 하나씩 나열하며 설명하지 않는다. 여러 스토리에 공통으로 흐르는
              쟁점 · 경향 · 논쟁 지점을 묶어서 서술한다.
            - 스토리마다 커뮤니티 반응이 갈리면 그 차이도 흐름의 일부로 녹여 설명한다.
            - 입력에 없는 내용을 지어내지 않는다.
            - 산문 한 덩어리로 답한다. 목록이나 번호를 매기지 않는다.
            - 입력이 영어여도 요약은 한국어로 쓴다. 고유명사 · 제품명은 원문 표기 그대로 둔다.
            """;

    private final ChatClient chatClient;

    public TopicSummarizerShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String summarize(String techField, List<AnalysisTarget> targets) {
        String user = "기술 분야: %s\n\n스토리:\n%s".formatted(techField,
                targets.stream().map(TopicSummarizerShell::render).collect(Collectors.joining("\n---\n")));

        return chatClient.prompt()
                .system(SYSTEM)
                .user(user)
                .call()
                .content();
    }

    private static String render(AnalysisTarget target) {
        return "제목: %s\n요약: %s\n커뮤니티 반응: %s".formatted(
                target.title(), target.summary(), target.communityReaction());
    }
}
