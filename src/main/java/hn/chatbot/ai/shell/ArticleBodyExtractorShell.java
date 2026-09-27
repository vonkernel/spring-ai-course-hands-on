package hn.chatbot.ai.shell;

import hn.chatbot.ai.ArticleBodyExtractor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 5단계 — 제목을 앵커로 삼아 정제 텍스트에서 본문만 남긴다. 메뉴·관련기사·푸터를 제외한다.
 *
 * ChatClient 는 주입돼 있다.
 */
@Component
public class ArticleBodyExtractorShell implements ArticleBodyExtractor {

    private final ChatClient chatClient;

    public ArticleBodyExtractorShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public Optional<String> extract(String title, String cleanedText) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
