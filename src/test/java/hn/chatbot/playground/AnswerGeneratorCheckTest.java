package hn.chatbot.playground;

import hn.chatbot.ai.AnswerGenerator;
import hn.chatbot.service.chat.SearchEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerGenerator 확인. 답변 규칙을 system 에 넣고, 준비된 근거로 답하게 해 결과를 본다.
 * 근거는 Q&A 에서 모델이 받는 것과 같은 JSON(SearchEvidence.promptJson)이다. 적재된 데이터는 필요 없다.
 *
 *   ./gradlew playground --tests '*AnswerGeneratorCheckTest'
 */
@Tag("playground")
@SpringBootTest
class AnswerGeneratorCheckTest {

    private static final List<SearchEvidence.Item> EVIDENCE = List.of(
            new SearchEvidence.Item(1, 101L, "Claude Code struggles with large refactors",
                    "https://example.com/101", List.of(3, 4), "OPINION_ESSAY",
                    "AI 코딩 에이전트가 여러 파일에 걸친 리팩터링에서 맥락을 잃는다는 개발자 경험담이다.",
                    "많은 개발자가 동의했고, 작은 단계로 나누면 낫다는 의견이 있었다.",
                    null,
                    "The agent kept no memory of decisions made earlier in the session."),
            new SearchEvidence.Item(2, 103L, "Why AI agents still need human code review",
                    "https://example.com/103", List.of(2, 4), "TECHNICAL_DEEP_DIVE",
                    "코딩 어시스턴트가 존재하지 않는 API 를 호출하고 조용한 버그를 만든다는 분석이다.",
                    "리뷰어들은 예전보다 미묘한 버그가 늘었다고 말한다.",
                    "에이전트가 쓴 변경도 사람이 리뷰하는 절차를 유지해야 한다.",
                    null));

    @Autowired AnswerGenerator generator;
    @Autowired ChatClient.Builder builder;

    @Test
    @DisplayName("답변 규칙을 따라 근거로만 답한다")
    void answersWithRules() {
        String rules = generator.answerRules();
        Playground.title("답변 규칙");
        System.out.println(rules);

        List<String> pieces = builder.build().prompt()
                .system(rules)
                .user("근거:\n" + SearchEvidence.promptJson(EVIDENCE) + "\n\n질문: 코딩 에이전트의 한계가 뭐야?")
                .stream()
                .content()
                .doOnNext(System.out::print)
                .collectList()
                .block();
        System.out.println();

        assertThat(rules).as("규칙이 비어 있으면 안 된다").isNotBlank();
        assertThat(pieces).as("답이 조각으로 흘러와야 한다").hasSizeGreaterThan(1);
        assertThat(String.join("", pieces)).isNotBlank();
    }
}
