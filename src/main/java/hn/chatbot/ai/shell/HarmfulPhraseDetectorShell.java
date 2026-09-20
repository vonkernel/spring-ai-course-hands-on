package hn.chatbot.ai.shell;

import hn.chatbot.ai.HarmfulPhraseDetector;
import hn.chatbot.ai.MaskingResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 3단계 — 검열에 걸린 댓글에서 유해 구간만 추출한다. 원문을 다시 쓰게 하면 내용이 변조되므로 치환할 구간만 받는다.
 *
 * ChatClient 는 주입돼 있다.
 */
@Component
public class HarmfulPhraseDetectorShell implements HarmfulPhraseDetector {

    private static final String SYSTEM = """
            너는 댓글에서 검열에 걸릴 만한 유해 표현(모욕, 폭력·자해 조장, 혐오 표현 등)이
            들어 있는 구간만 골라내는 역할을 한다.

            규칙
            - 돌려주는 각 구간은 원문에 있는 문자열을 한 글자도 바꾸지 않고 그대로 옮긴 것이어야
              한다. 대소문자 · 띄어쓰기 · 구두점까지 원문과 동일해야 애플리케이션이 그 문자열로
              원문을 찾아 치환할 수 있다.
            - 원문을 고쳐 쓰거나 요약하거나 순화한 문장을 새로 만들어내지 않는다.
            - 유해하지 않은 문장이나 문맥 설명은 포함하지 않는다. 유해한 단어 · 구절만 최소
              단위로 담는다.
            - 유해한 표현이 없으면 빈 목록을 돌려준다.
            """;

    private final ChatClient chatClient;

    public HarmfulPhraseDetectorShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public MaskingResult detect(String text) {
        MaskingResult result = chatClient.prompt()
                .system(SYSTEM)
                .user(u -> u.text("댓글:\n{comment}").param("comment", text))
                .call()
                .entity(MaskingResult.class);

        // 구조화 출력은 모델이 필드를 빼면 null 을 준다. 부르는 쪽이 바로 순회한다.
        if (result == null || result.harmfulPhrases() == null) {
            return new MaskingResult(List.of());
        }
        return result;
    }
}
