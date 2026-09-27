package hn.chatbot.ai.shell;

import hn.chatbot.ai.ArticleChunker;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 7단계 — 문단 단위로 청킹한다. 경계가 식별되지 않거나 한 문단이 지나치게 길면 하드 리밋으로 나눈다.
 *
 * 순수 텍스트 분할이라 모델 호출이 필요 없다.
 */
@Component
public class ArticleChunkerShell implements ArticleChunker {

    @Override
    public List<String> chunk(String body) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
