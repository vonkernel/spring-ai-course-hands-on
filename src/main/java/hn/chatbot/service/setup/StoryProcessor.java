package hn.chatbot.service.setup;

/**
 * 스토리 하나를 파이프라인 2~8단계로 처리한다. 뼈대(PipelineSetupService)가 스토리마다 호출한다.
 * 구현(DefaultStoryProcessor)은 완성본이다. 인덱싱 파이프라인의 E · T · L 을 세 객체에 나눠 맡긴다.
 *
 * 8단계
 *
 * - 1 수집: 인기 스토리 목록. 뼈대가 한다
 * - E StoryExtractor
 *   - 2 중복 확인: SetupQuery.isProcessed 면 SKIPPED. 댓글을 받아오기 전에 확인한다. 이어서 스토리와 댓글을 수집한다
 * - T StoryTransformer
 *   - 3 댓글 검열: CommentModerator 에 걸린 댓글만 HarmfulPhraseDetector 구간을 PipelinePolicy.MASK 로 치환한다
 *   - 4 기계적 정제: ArticleSource.fetch 결과를 PipelinePolicy.check 로 판정. passed() 가 아니면 제외
 *   - 5 본문 추출: ArticleBodyExtractor. 찾지 못하면 제외
 *   - 6 분석: IssueAnalyzer 에 최상위 댓글 TOP_COMMENTS 개를 함께 넘긴다. 부적합이면 제외
 *   - 7 청킹: ArticleChunker. 순서가 곧 seq
 * - L StoryLoader
 *   - 8 저장 · 적재: SetupStore 로 원본을 모두 저장하고, 끝까지 통과한 스토리만 EmbeddingIndexer 로 적재한다
 *
 * 판정 결과와 무관하게 원본은 모두 저장한다. 제외할 때도 article 행을 남긴다.
 * 그 행이 다음 실행의 중복 확인 기준이다.
 *
 * 단계를 통과할 때마다 tracker.passed(단계) 를 호출하면 화면의 단계 표시가 움직인다.
 */
public interface StoryProcessor {

    StoryOutcome process(long storyId, StageTracker tracker);
}
