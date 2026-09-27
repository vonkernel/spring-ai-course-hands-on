package hn.chatbot.service.setup.model;

import hn.chatbot.ai.IssueAnalysis;
import hn.chatbot.domain.Article;
import hn.chatbot.domain.Comment;
import hn.chatbot.domain.Story;

import java.util.List;

/**
 * 변환(T)을 마친 스토리 하나. StoryTransformer 가 만들고 StoryLoader 가 저장 · 적재한다.
 *
 * 제외된 스토리도 여기까지 온다. 판정 결과와 무관하게 원본을 모두 저장하기 때문이다.
 *
 * - 4 · 5단계에서 제외: analysis 는 null, chunks 는 비어 있다
 * - 6단계에서 부적합: analysis 는 있지만 suitable 이 false, chunks 는 비어 있다
 * - 끝까지 통과: analysis 가 적합이고 chunks 가 있다. 이것만 벡터 스토어에 적재한다
 */
public record TransformedStory(Story story, List<Comment> comments, Article article,
                               IssueAnalysis analysis, List<String> chunks) {

    /** 벡터 스토어에 적재할 대상인가. */
    public boolean indexable() {
        return analysis != null && analysis.suitable() && !chunks.isEmpty();
    }
}
