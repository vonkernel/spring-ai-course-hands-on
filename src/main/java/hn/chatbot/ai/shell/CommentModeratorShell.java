package hn.chatbot.ai.shell;

import hn.chatbot.ai.CommentModerator;
import hn.chatbot.ai.ModerationProperties;
import hn.chatbot.ai.ModerationVerdict;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.stereotype.Component;

/**
 * 3단계 — ModerationModel 로 댓글의 카테고리별 점수를 받는다.
 *
 * isFlagged() 를 그대로 쓰지 않는다. 우리가 고른 카테고리의 점수만
 * 임계값과 비교한다. harassment 계열은 뺀다 — 기술 커뮤니티의 제품·기업 비판이 대량으로 걸린다.
 * 카테고리 목록과 임계값은 application.yml 에서 주입된다.
 *
 * CommentModeratorCheckTest 로 확인한다.
 */
@Component
public class CommentModeratorShell implements CommentModerator {

    private final ModerationModel moderationModel;
    private final ModerationProperties properties;

    public CommentModeratorShell(ModerationModel moderationModel, ModerationProperties properties) {
        this.moderationModel = moderationModel;
        this.properties = properties;
    }

    @Override
    public ModerationVerdict inspect(String text) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
