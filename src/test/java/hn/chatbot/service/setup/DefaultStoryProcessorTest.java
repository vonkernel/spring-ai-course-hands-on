package hn.chatbot.service.setup;

import hn.chatbot.ai.EmbeddingIndexer;
import hn.chatbot.ai.IssueAnalysis;
import hn.chatbot.ai.MaskingResult;
import hn.chatbot.ai.ModerationVerdict;
import hn.chatbot.domain.Analysis;
import hn.chatbot.domain.Article;
import hn.chatbot.domain.ArticleStatus;
import hn.chatbot.domain.BodyChunk;
import hn.chatbot.domain.Comment;
import hn.chatbot.domain.Story;
import hn.chatbot.service.setup.model.CollectedComment;
import hn.chatbot.service.setup.model.CollectedStory;
import hn.chatbot.service.setup.model.Exclusions;
import hn.chatbot.service.setup.model.FetchOutcome;
import hn.chatbot.service.setup.model.FetchedArticle;
import hn.chatbot.service.setup.model.LogKind;
import hn.chatbot.service.setup.model.LogState;
import hn.chatbot.service.setup.port.SetupQuery;
import hn.chatbot.service.setup.port.SetupStore;
import hn.chatbot.service.setup.port.StorySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultStoryProcessorTest {

    private static final String LONG_BODY = "Coding agents lose context on large refactors. ".repeat(80);

    private static final CollectedStory STORY = new CollectedStory(7L, "title", "https://example.com/a", "alice",
            10, 1, null, Instant.now(),
            List.of(new CollectedComment(70L, 7L, "bob", "this is bad words here", (short) 1, Instant.now())));

    private static final IssueAnalysis SUITABLE = new IssueAnalysis("요약", "NEWS_REPORT", "AI_LLM",
            List.of("AI"), "반응", null, true, "");

    private final RecordingStore store = new RecordingStore();
    private final List<Integer> passed = new ArrayList<>();
    private final List<String> indexed = new ArrayList<>();

    private final StageTracker tracker = new StageTracker() {
        @Override
        public void passed(int stage) {
            passed.add(stage);
        }

        @Override
        public void log(LogKind kind, long storyId, String title, String meta, LogState state) {
        }
    };

    private final EmbeddingIndexer indexer = new EmbeddingIndexer() {
        @Override
        public void indexSummary(long storyId, IssueAnalysis analysis) {
            indexed.add("summary");
        }

        @Override
        public void indexBody(long storyId, IssueAnalysis analysis, List<String> chunks) {
            indexed.add("body:" + chunks.size());
        }
    };

    @Test
    @DisplayName("끝까지 통과하면 원본과 분석 · 청크를 저장하고 벡터 스토어에 적재한다")
    void completes() {
        DefaultStoryProcessor processor = processor(false, FetchOutcome.OK, SUITABLE);

        StoryOutcome outcome = processor.process(7L, tracker);

        assertThat(outcome).isEqualTo(StoryOutcome.COMPLETED);
        assertThat(passed).containsExactly(2, 3, 4, 5, 6, 7, 8);
        assertThat(store.saved).containsExactly("story", "comments", "article:PASS", "analysis", "chunks:2");
        assertThat(store.comments.get(0).getText()).as("걸린 댓글은 유해 구간이 치환된다").contains("***").doesNotContain("bad");
        assertThat(indexed).containsExactly("summary", "body:2");
    }

    @Test
    @DisplayName("이미 처리한 스토리는 수집하지 않고 건너뛴다")
    void skips() {
        StoryOutcome outcome = processor(true, FetchOutcome.OK, SUITABLE).process(7L, tracker);

        assertThat(outcome).isEqualTo(StoryOutcome.SKIPPED);
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("4단계에서 제외돼도 원본과 article 행은 저장하고 적재하지 않는다")
    void excludesButSavesOriginals() {
        StoryOutcome outcome = processor(false, FetchOutcome.PDF, SUITABLE).process(7L, tracker);

        assertThat(outcome).isEqualTo(StoryOutcome.EXCLUDED);
        assertThat(store.saved).containsExactly("story", "comments", "article:PDF");
        assertThat(indexed).isEmpty();
    }

    @Test
    @DisplayName("부적합 판정은 분석까지 저장하고 청크와 적재는 하지 않는다")
    void unsuitable() {
        IssueAnalysis unsuitable = new IssueAnalysis("요약", "NEWS_REPORT", "NON_TECHNICAL",
                List.of(), "반응", null, false, "광고");

        StoryOutcome outcome = processor(false, FetchOutcome.OK, unsuitable).process(7L, tracker);

        assertThat(outcome).isEqualTo(StoryOutcome.EXCLUDED);
        assertThat(store.saved).containsExactly("story", "comments", "article:PASS", "analysis");
        assertThat(indexed).isEmpty();
    }

    @Test
    @DisplayName("부품이 비어 있으면 아무것도 저장하지 않고 예외를 그대로 올린다")
    void notImplementedSavesNothing() {
        StoryTransformer transformer = new StoryTransformer(text -> verdict(false), text -> new MaskingResult(List.of()),
                url -> new FetchedArticle(url, FetchOutcome.OK, LONG_BODY),
                (title, text) -> {
                    throw new UnsupportedOperationException();
                },
                (title, body, comments) -> SUITABLE, body -> List.of(body));
        DefaultStoryProcessor processor = new DefaultStoryProcessor(
                new StoryExtractor(query(false), source()), transformer, new StoryLoader(store, indexer));

        assertThatThrownBy(() -> processor.process(7L, tracker)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(store.saved).isEmpty();
    }

    private DefaultStoryProcessor processor(boolean processed, FetchOutcome fetch, IssueAnalysis analysis) {
        StoryTransformer transformer = new StoryTransformer(
                text -> verdict(text.contains("bad")),
                text -> new MaskingResult(List.of("bad")),
                url -> new FetchedArticle(url, fetch, fetch == FetchOutcome.OK ? LONG_BODY : null),
                (title, text) -> Optional.of(text),
                (title, body, comments) -> analysis,
                body -> List.of(body.substring(0, 100), body.substring(100)));
        return new DefaultStoryProcessor(new StoryExtractor(query(processed), source()), transformer,
                new StoryLoader(store, indexer));
    }

    private static ModerationVerdict verdict(boolean flagged) {
        return new ModerationVerdict(flagged, flagged ? "harassment" : null, flagged ? 0.9 : 0.0, Map.of());
    }

    private static StorySource source() {
        return new StorySource() {
            @Override
            public List<Long> bestStoryIds(int limit) {
                return List.of(7L);
            }

            @Override
            public Optional<CollectedStory> collect(long storyId) {
                return Optional.of(STORY);
            }
        };
    }

    private static SetupQuery query(boolean processed) {
        return new SetupQuery() {
            @Override
            public boolean isProcessed(long storyId) {
                return processed;
            }

            @Override
            public int countSearchableStories() {
                return 0;
            }

            @Override
            public Exclusions exclusions() {
                return null;
            }
        };
    }

    private static final class RecordingStore implements SetupStore {

        final List<String> saved = new ArrayList<>();
        List<Comment> comments = List.of();

        @Override
        public void saveStory(Story story) {
            saved.add("story");
        }

        @Override
        public void saveComments(List<Comment> comments) {
            this.comments = comments;
            saved.add("comments");
        }

        @Override
        public void saveArticle(Article article) {
            saved.add("article:" + article.getStatus());
        }

        @Override
        public void saveAnalysis(Analysis analysis) {
            saved.add("analysis");
        }

        @Override
        public void saveChunks(List<BodyChunk> chunks) {
            saved.add("chunks:" + chunks.size());
        }
    }
}
