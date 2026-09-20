package hn.chatbot.ai.shell;

import hn.chatbot.ai.CommentModerator;
import hn.chatbot.ai.ModerationProperties;
import hn.chatbot.ai.ModerationVerdict;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

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
        ModerationResult result = moderationModel.call(new ModerationPrompt(text))
                .getResult().getOutput().getResults().get(0);

        Map<String, Double> scores = toScores(result.getCategoryScores());

        // 최고점은 관찰용이라 전 카테고리 기준이다. 판정만 채택 카테고리로 좁힌다.
        Map.Entry<String, Double> top = scores.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .orElseThrow();

        // 카테고리 목록은 설정값이라 모델이 채우지 않는 이름이 들어올 수 있다.
        // get 이 null 을 주면 언박싱에서 터지므로 없는 값은 0 으로 받는다.
        boolean flagged = properties.categories().stream()
                .anyMatch(category -> scores.getOrDefault(category, 0.0) >= properties.threshold());

        return new ModerationVerdict(flagged, top.getKey(), top.getValue(), scores);
    }

    private static Map<String, Double> toScores(CategoryScores scores) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("sexual", scores.getSexual());
        result.put("hate", scores.getHate());
        result.put("harassment", scores.getHarassment());
        result.put("self-harm", scores.getSelfHarm());
        result.put("sexual/minors", scores.getSexualMinors());
        result.put("hate/threatening", scores.getHateThreatening());
        result.put("violence/graphic", scores.getViolenceGraphic());
        result.put("self-harm/intent", scores.getSelfHarmIntent());
        result.put("self-harm/instructions", scores.getSelfHarmInstructions());
        result.put("harassment/threatening", scores.getHarassmentThreatening());
        result.put("violence", scores.getViolence());
        result.put("dangerous-and-criminal-content", scores.getDangerousAndCriminalContent());
        result.put("health", scores.getHealth());
        result.put("financial", scores.getFinancial());
        result.put("law", scores.getLaw());
        result.put("pii", scores.getPii());
        return result;
    }
}
