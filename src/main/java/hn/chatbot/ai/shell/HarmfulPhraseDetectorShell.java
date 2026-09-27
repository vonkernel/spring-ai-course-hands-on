package hn.chatbot.ai.shell;

import hn.chatbot.ai.HarmfulPhraseDetector;
import hn.chatbot.ai.MaskingResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 3단계 — 검열에 걸린 댓글에서 유해 구간만 추출한다. 원문을 다시 쓰게 하면 내용이 변조되므로 치환할 구간만 받는다.
 *
 * ChatClient 는 주입돼 있다.
 */
@Component
public class HarmfulPhraseDetectorShell implements HarmfulPhraseDetector {

    private final ChatClient chatClient;

    public HarmfulPhraseDetectorShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public MaskingResult detect(String text) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
