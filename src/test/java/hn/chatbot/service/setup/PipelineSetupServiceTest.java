package hn.chatbot.service.setup;

import hn.chatbot.service.setup.model.CollectedStory;
import hn.chatbot.service.setup.model.ExclusionGroup;
import hn.chatbot.service.setup.model.Exclusions;
import hn.chatbot.service.setup.model.LogKind;
import hn.chatbot.service.setup.model.LogState;
import hn.chatbot.service.setup.model.SetupOutcome;
import hn.chatbot.service.setup.model.SetupProgress;
import hn.chatbot.service.setup.model.SetupState;
import hn.chatbot.service.setup.port.SetupQuery;
import hn.chatbot.service.setup.port.StorySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSetupServiceTest {

    private final List<SetupProgress> published = new CopyOnWriteArrayList<>();
    private final List<SetupOutcome> outcomes = new CopyOnWriteArrayList<>();

    private final SetupProgressPublisher publisher = new SetupProgressPublisher() {
        @Override
        public void publish(SetupProgress progress) {
            published.add(progress);
        }

        @Override
        public void complete(SetupOutcome outcome) {
            outcomes.add(outcome);
        }
    };

    private final SetupQuery query = new SetupQuery() {
        @Override
        public boolean isProcessed(long storyId) {
            return false;
        }

        @Override
        public int countSearchableStories() {
            return 0;
        }

        @Override
        public Exclusions exclusions() {
            return new Exclusions(new ExclusionGroup(0, Map.of()), new ExclusionGroup(0, Map.of()));
        }
    };

    private final StorySource source = new StorySource() {
        @Override
        public List<Long> bestStoryIds(int limit) {
            return List.of(1L, 2L, 3L).subList(0, limit);
        }

        @Override
        public Optional<CollectedStory> collect(long storyId) {
            return Optional.empty();
        }
    };

    @Test
    @DisplayName("결과를 완료 · 건너뜀 · 제외 건수로 옮기고 단계 통과를 반영하며 항등식을 지킨다")
    void countsOutcomes() {
        StoryProcessor processor = (id, tracker) -> {
            tracker.passed(2);
            tracker.log(LogKind.EXTRACT, id, "t", "m", LogState.OK);
            return switch ((int) id) {
                case 1 -> StoryOutcome.COMPLETED;
                case 2 -> StoryOutcome.EXCLUDED;
                default -> StoryOutcome.SKIPPED;
            };
        };
        PipelineSetupService service = new PipelineSetupService(source, processor, query, publisher, 1, 1);

        service.run(3);
        SetupProgress done = awaitEnd(service);

        assertThat(done.state()).isEqualTo(SetupState.DONE);
        assertThat(done.completed()).isEqualTo(1);
        assertThat(done.skipped()).isEqualTo(1);
        assertThat(done.excluded()).isEqualTo(1);
        assertThat(done.stages().get(1).count()).isEqualTo(3);
        assertThat(published).allSatisfy(p -> assertThat(p.target())
                .isEqualTo(p.completed() + p.skipped() + p.inProgress() + p.excluded() + p.pending()));
        assertThat(outcomes).hasSize(1);
    }

    @Test
    @DisplayName("부품이 비어 있으면 진행 화면 시연으로 끝까지 흐른다")
    void demoWhenNotImplemented() {
        StoryProcessor empty = (id, tracker) -> {
            throw new UnsupportedOperationException();
        };
        PipelineSetupService service = new PipelineSetupService(source, empty, query, publisher, 1, 1);

        service.run(3);
        SetupProgress done = awaitEnd(service);

        assertThat(done.state()).isEqualTo(SetupState.DONE);
        assertThat(done.recentLogs()).anySatisfy(l -> assertThat(l.meta()).contains("진행 화면만"));
        assertThat(published).allSatisfy(p -> assertThat(p.target())
                .isEqualTo(p.completed() + p.skipped() + p.inProgress() + p.excluded() + p.pending()));
    }

    private static SetupProgress awaitEnd(PipelineSetupService service) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            SetupProgress p = service.progress();
            if (p.state() != SetupState.RUNNING) {
                return p;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("셋업이 끝나지 않았다");
    }
}
