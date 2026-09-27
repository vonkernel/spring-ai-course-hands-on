package hn.chatbot.ai.shell;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.RelevanceJudge;
import hn.chatbot.ai.RelevantStory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 후보 중 질의에 실제로 답이 되는 것을 고르고, 근거 대목을 뽑는다.
 * 계약은 RelevanceJudge 의 Javadoc 에 있다.
 *
 * ChatClient 는 주입돼 있다. 결과는 구조화 출력으로 받는다.
 */
@Component
public class RelevanceJudgeShell implements RelevanceJudge {

    private final ChatClient chatClient;

    public RelevanceJudgeShell(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public List<RelevantStory> selectRelevant(String question, List<AnalysisTarget> targets, int max) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
