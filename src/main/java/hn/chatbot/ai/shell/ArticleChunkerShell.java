package hn.chatbot.ai.shell;

import hn.chatbot.ai.ArticleChunker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 7단계 — 문단 단위로 청킹한다. 경계가 식별되지 않거나 한 문단이 지나치게 길면 하드 리밋으로 나눈다.
 *
 * 순수 텍스트 분할이라 모델 호출이 필요 없다.
 */
@Component
public class ArticleChunkerShell implements ArticleChunker {

    private static final int HARD_LIMIT = 2_000;

    @Override
    public List<String> chunk(String body) {
        List<String> paragraphs = Arrays.stream(body.split("\\n\\s*\\n"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .toList();

        if (paragraphs.isEmpty()) {
            paragraphs = List.of(body.strip());
        }

        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= HARD_LIMIT) {
                chunks.add(paragraph);
                continue;
            }
            for (int i = 0; i < paragraph.length(); i += HARD_LIMIT) {
                chunks.add(paragraph.substring(i, Math.min(i + HARD_LIMIT, paragraph.length())));
            }
        }
        return chunks;
    }
}
