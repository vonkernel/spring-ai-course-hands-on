package hn.chatbot.ai.shell;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.RelevanceJudge;
import hn.chatbot.ai.RelevantStory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 후보 중 질의에 실제로 답이 되는 것을 고르고, 근거 대목을 뽑는다.
 * 계약은 RelevanceJudge 의 Javadoc 에 있다.
 *
 * ChatClient 는 주입돼 있다. 결과는 구조화 출력으로 받는다.
 */
@Component
public class RelevanceJudgeShell implements RelevanceJudge {

    private static final String SYSTEM = """
            너는 여러 검색 후보 중에서 질문에 실제로 답이 되는 것만 고르고,
            그 근거가 되는 원문 대목을 뽑는 역할을 한다.

            각 후보는 제목 · 요약 · 원문 앞부분(excerpt)으로 주어진다. 요약만 보고
            판단하지 말고 excerpt 도 읽고 실제로 질문에 답이 되는지 확인한다.

            규칙
            - 질문에 실제로 답이 되는 후보만 고른다. 관련 없는 후보는 뺀다.
            - 관련 있는 후보가 여러 개면 관련도가 높은 순으로 정렬해 최대 개수까지 고른다.
              관련 있는 후보가 하나라도 있으면 최소 1건은 남긴다.
            - 고른 후보마다 passage 를 채운다. passage 는 그 후보의 excerpt 안에 실제로
              있는 문장을 그대로 옮긴 것이어야 한다. 요약하거나 새로 쓰지 않는다.
              excerpt 안에서 명확한 근거 문장을 찾지 못하면 passage 를 빈 문자열로 둔다.
            - 결과에는 입력에 있는 storyId 만 쓴다. 새로운 id 를 만들어내지 않는다.
            - 돌려주는 순서가 근거 카드의 표시 순서다. 관련도가 높은 것을 앞에 둔다.
            """;

    private final ChatClient chatClient;

    public RelevanceJudgeShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public List<RelevantStory> selectRelevant(String question, List<AnalysisTarget> targets, int max) {
        String user = "질문: %s\n최대 선택 개수: %d\n\n후보:\n%s".formatted(question, max,
                targets.stream().map(RelevanceJudgeShell::render).collect(Collectors.joining("\n---\n")));

        List<RelevantStory> selected = chatClient.prompt()
                .system(SYSTEM)
                .user(user)
                .call()
                .entity(new ParameterizedTypeReference<List<RelevantStory>>() {
                });

        Set<Long> knownIds = targets.stream().map(AnalysisTarget::storyId).collect(Collectors.toSet());
        List<RelevantStory> verified = selected == null ? List.of() : selected.stream()
                .filter(story -> knownIds.contains(story.storyId()))
                .limit(max)
                .map(story -> story.passage() == null || story.passage().isBlank()
                        ? new RelevantStory(story.storyId(), null)
                        : story)
                .toList();

        // 최소 1건 계약. 모델이 빈 배열을 주거나 지어낸 id 가 전부 걸러지면 답할 근거가 없어진다.
        // 프롬프트로 지시하는 것으로 끝내지 않고 여기서 되받는다.
        if (verified.isEmpty() && !targets.isEmpty()) {
            return List.of(new RelevantStory(targets.get(0).storyId(), null));
        }
        return verified;
    }

    private static String render(AnalysisTarget target) {
        return "storyId: %d\n제목: %s\n요약: %s\nexcerpt: %s".formatted(
                target.storyId(), target.title(), target.summary(), target.excerpt());
    }
}
