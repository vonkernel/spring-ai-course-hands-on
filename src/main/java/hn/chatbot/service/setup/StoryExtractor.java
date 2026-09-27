package hn.chatbot.service.setup;

import hn.chatbot.service.setup.model.CollectedStory;
import hn.chatbot.service.setup.port.SetupQuery;
import hn.chatbot.service.setup.port.StorySource;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 인덱싱 파이프라인의 추출(E). 2단계 중복 확인과 스토리 · 댓글 수집을 맡는다. 완성본이다.
 *
 * 중복 확인은 댓글을 받아오기 전에 한다. 이미 처리한 스토리의 댓글 트리를 다시 순회하지 않기 위해서다.
 * 기준은 article 행의 존재다(SetupQuery.isProcessed).
 */
@Component
public class StoryExtractor {

    private final SetupQuery setupQuery;
    private final StorySource storySource;

    public StoryExtractor(SetupQuery setupQuery, StorySource storySource) {
        this.setupQuery = setupQuery;
        this.storySource = storySource;
    }

    /** 이미 처리한 스토리면 빈 값이다. */
    public Optional<CollectedStory> extract(long storyId, StageTracker tracker) {
        if (setupQuery.isProcessed(storyId)) {
            return Optional.empty();
        }
        tracker.passed(2);

        return Optional.of(storySource.collect(storyId)
                .orElseThrow(() -> new NoSuchElementException("스토리를 찾을 수 없습니다: " + storyId)));
    }
}
