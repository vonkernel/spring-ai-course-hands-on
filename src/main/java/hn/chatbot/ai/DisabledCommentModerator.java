package hn.chatbot.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 검열을 끈 상태의 CommentModerator. 완성본이다.
 *
 * app.moderation.enabled 가 false 이거나 없으면 이 빈이 @Primary 로 올라가
 * CommentModeratorShell 대신 주입된다. 모델을 부르지 않고 전부 통과시킨다.
 *
 * 기본값이 꺼짐인 이유는 Moderation 의 분당 요청 한도가 낮기 때문이다.
 * 댓글 하나마다 호출하는 구조라 셋업을 병렬로 돌리면 가장 먼저 429 가 난다.
 * Spring AI 1.1.8 의 ModerationPrompt 는 문자열 하나만 담아 배치로 묶을 수도 없다.
 *
 * 세션 3 의 검열 실습은 이 설정과 무관하다. CommentModeratorCheckTest 가
 * app.moderation.enabled=true 로 띄워 CommentModeratorShell 을 직접 확인한다.
 */
@Primary
@Component
@ConditionalOnProperty(name = "app.moderation.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledCommentModerator implements CommentModerator {

    @Override
    public ModerationVerdict inspect(String text) {
        return new ModerationVerdict(false, "disabled", 0.0, Map.of());
    }
}
