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

    private static final String NOT_FOUND = "NONE";

    private static final String SYSTEM = """
            너는 태그를 제거해 메뉴 · 본문 · 관련기사 · 푸터가 뒤섞인 텍스트에서 기사 본문만
            골라내는 역할을 한다. 제목을 단서로 삼아 그 기사의 본문에 해당하는 부분을 찾는다.

            규칙
            - 내비게이션 메뉴, 로그인 · 가입 · 쿠키 · 구독 안내, 관련 기사 목록, 댓글, 푸터,
              저작권 표시는 모두 제외한다.
            - 본문 문장은 입력에 있는 그대로 옮긴다. 요약하거나 새로 쓰거나 표현을 바꾸지 않는다.
            - 문단 사이의 빈 줄은 그대로 유지한다.
            - 입력에 없는 내용을 절대 덧붙이지 않는다.
            - 본문이라 할 만한 내용을 찾지 못하면 다른 말 없이 정확히 "%s" 만 출력한다.
            - 출력에는 본문 텍스트, 또는 "%s" 외의 다른 말(설명, 인사, 마크다운 등)을 포함하지 않는다.
            """.formatted(NOT_FOUND, NOT_FOUND);

    private final ChatClient chatClient;

    public ArticleBodyExtractorShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public Optional<String> extract(String title, String cleanedText) {
        String response = chatClient.prompt()
                .system(SYSTEM)
                .user(u -> u.text("제목: {title}\n\n텍스트:\n{text}")
                        .param("title", title)
                        .param("text", cleanedText))
                .call()
                .content();

        String body = response.strip();
        if (body.isEmpty() || body.equals(NOT_FOUND)) {
            return Optional.empty();
        }
        return Optional.of(body);
    }
}
