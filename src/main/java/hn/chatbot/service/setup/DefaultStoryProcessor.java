package hn.chatbot.service.setup;

import hn.chatbot.service.setup.model.CollectedStory;
import hn.chatbot.service.setup.model.TransformedStory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 스토리 하나를 2~8단계로 처리한다. 완성본이다.
 *
 * 인덱싱 파이프라인의 E · T · L 을 담당 객체에 맡기고 차례로 부른다.
 *
 *   StoryExtractor   (E) 2 중복 확인, 스토리 · 댓글 수집
 *   StoryTransformer (T) 3 검열 → 4 정제 → 5 본문 추출 → 6 분석 → 7 청킹
 *   StoryLoader      (L) 8 저장 · 벡터 스토어 적재
 *
 * 모델 호출은 전부 실습에서 만든 부품 안에 있다. 부품이 아직 비어 있으면 그 부품이 던진
 * UnsupportedOperationException 이 그대로 올라가고, 실행 뼈대가 진행 화면 시연으로 바꾼다.
 */
@Component
public class DefaultStoryProcessor implements StoryProcessor {

    private final StoryExtractor extractor;
    private final StoryTransformer transformer;
    private final StoryLoader loader;

    public DefaultStoryProcessor(StoryExtractor extractor, StoryTransformer transformer, StoryLoader loader) {
        this.extractor = extractor;
        this.transformer = transformer;
        this.loader = loader;
    }

    @Override
    public StoryOutcome process(long storyId, StageTracker tracker) {
        Optional<CollectedStory> collected = extractor.extract(storyId, tracker);
        if (collected.isEmpty()) {
            return StoryOutcome.SKIPPED;
        }

        TransformedStory transformed = transformer.transform(collected.get(), tracker);
        loader.load(transformed, tracker);

        return transformed.indexable() ? StoryOutcome.COMPLETED : StoryOutcome.EXCLUDED;
    }
}
