package hn.chatbot.service.setup;

import hn.chatbot.ai.EmbeddingIndexer;
import hn.chatbot.domain.BodyChunk;
import hn.chatbot.service.setup.model.TransformedStory;
import hn.chatbot.service.setup.port.SetupStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 인덱싱 파이프라인의 적재(L). 8단계 저장과 벡터 스토어 적재를 맡는다. 완성본이다.
 *
 * 판정 결과와 무관하게 원본(스토리 · 댓글 · article)은 모두 저장한다. 제외된 스토리의
 * article 행이 다음 실행의 중복 확인 기준이다. 분석이 있으면 부적합이라도 저장한다.
 *
 * 벡터 스토어 적재는 끝까지 통과한 스토리만 한다. EmbeddingIndexer 의 두 메서드로
 * 요약과 청크를 적재하고, 청크 적재에도 분석 결과를 넘겨 필터 값을 싣는다.
 */
@Component
public class StoryLoader {

    private final SetupStore store;
    private final EmbeddingIndexer embeddingIndexer;

    public StoryLoader(SetupStore store, EmbeddingIndexer embeddingIndexer) {
        this.store = store;
        this.embeddingIndexer = embeddingIndexer;
    }

    public void load(TransformedStory story, StageTracker tracker) {
        long storyId = story.story().getId();

        store.saveStory(story.story());
        store.saveComments(story.comments());
        store.saveArticle(story.article());
        if (story.analysis() != null) {
            store.saveAnalysis(AnalysisMapper.toEntity(storyId, story.analysis()));
        }
        if (!story.indexable()) {
            return;
        }

        List<BodyChunk> chunks = new ArrayList<>();
        for (int seq = 0; seq < story.chunks().size(); seq++) {
            chunks.add(new BodyChunk(storyId, seq, story.chunks().get(seq)));
        }
        store.saveChunks(chunks);

        embeddingIndexer.indexSummary(storyId, story.analysis());
        embeddingIndexer.indexBody(storyId, story.analysis(), story.chunks());
        tracker.passed(8);
    }
}
