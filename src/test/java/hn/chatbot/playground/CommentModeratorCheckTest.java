package hn.chatbot.playground;

import com.fasterxml.jackson.databind.ObjectMapper;
import hn.chatbot.ai.CommentModerator;
import hn.chatbot.ai.ModerationProperties;
import hn.chatbot.ai.ModerationVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 콘텐츠 안전 필터링 확인.
 *
 * 이 테스트의 목적은 통과 여부가 아니라 카테고리별 점수가 어떻게 나오는지
 * 눈으로 확인하는 것이다. 그래서 단정은 값이 돌아오는지까지만 하고,
 * 실제 탐지 값을 실행 화면에 표로 출력한다.
 *
 * 픽스처는 카테고리별로 의도적으로 자극하는 영문 문장 5개씩 6개 카테고리 30건이다.
 * 실측에서 드러난 것 두 가지를 직접 보게 된다.
 *   - sexual 은 명시적 표현만 잡는다. 암시적 표현은 통과한다
 *   - hate 의도로 쓴 문장의 최고 점수 카테고리가 harassment 로 나온다.
 *       카테고리 경계가 직관과 다르다 — 우리가 harassment 계열을 제외한 이유다
 *
 * CommentModerator 가 비어 있는 동안에는 이 테스트가 실패한다.
 * 수강생이 구현을 채워 통과시킨다.
 *
 * app.moderation.enabled 를 켜서 띄운다. 애플리케이션 기본값은 꺼짐이고 그때는
 * DisabledCommentModerator 가 @Primary 로 올라가므로, 켜지 않으면 수강생이 채운
 * CommentModeratorShell 이 아니라 통과용 빈을 확인하게 된다.
 *
 *   ./gradlew playground --tests '*CommentModeratorCheckTest'
 */
@Tag("playground")
@SpringBootTest(properties = "app.moderation.enabled=true")
class CommentModeratorCheckTest {

    record Fixture(String kind, String intended, String text,
                   boolean flagged, Map<String, Boolean> categories, Map<String, Double> scores) {
    }

    @Autowired CommentModerator moderator;
    @Autowired ModerationProperties properties;

    @Test
    @DisplayName("픽스처 30건의 카테고리별 점수를 확인한다")
    void inspectFixtures() throws Exception {
        List<Fixture> fixtures = load();
        assertThat(fixtures).hasSize(30);

        System.out.printf("%n검사 대상 카테고리: %s%n임계값: %.2f%n%n",
                properties.categories(), properties.threshold());
        System.out.printf("%-12s %-8s %-26s %8s  %s%n",
                "의도", "걸림", "최고 점수 카테고리", "점수", "문장");
        System.out.println("-".repeat(110));

        int flaggedCount = 0;
        for (Fixture f : fixtures) {
            // 빈 구현 상태에서는 여기서 UnsupportedOperationException 이 난다.
            ModerationVerdict verdict = moderator.inspect(f.text());

            assertThat(verdict).as("판정이 돌아와야 한다").isNotNull();
            assertThat(verdict.scores()).as("카테고리별 점수가 있어야 한다").isNotEmpty();

            if (verdict.flagged()) {
                flaggedCount++;
            }
            System.out.printf("%-12s %-8s %-26s %8.3f  %s%n",
                    f.intended(),
                    verdict.flagged() ? "O" : "-",
                    verdict.topCategory(),
                    verdict.topScore(),
                    abbreviate(f.text()));
        }

        System.out.printf("%n30건 중 %d건이 임계값 %.2f 를 넘었다.%n",
                flaggedCount, properties.threshold());
        System.out.println("의도한 카테고리와 최고 점수 카테고리가 어긋나는 건을 눈으로 확인한다.");
    }

    @Test
    @DisplayName("실제 HN 댓글에서는 0건이 걸리는 것이 정상이다")
    void realCommentsPassThrough() {
        // 실측에서 실제 댓글 30건의 최고점이 0.285 였다. 임계값 0.85 와 3배 여유가 있다.
        String benign = "This resonates with me. The past few months, frontier AI labs "
                + "were rushing to announce incremental wins that nobody asked for.";

        ModerationVerdict verdict = moderator.inspect(benign);

        assertThat(verdict.flagged())
                .as("기술 커뮤니티의 평범한 댓글은 걸리지 않아야 한다")
                .isFalse();
        System.out.printf("%n평범한 댓글 — 최고 %s %.3f (임계값 %.2f)%n",
                verdict.topCategory(), verdict.topScore(), properties.threshold());
    }

    private List<Fixture> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/moderation-fixtures.json")) {
            return mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, Fixture.class));
        }
    }

    private static String abbreviate(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 48 ? flat : flat.substring(0, 47) + "…";
    }
}
