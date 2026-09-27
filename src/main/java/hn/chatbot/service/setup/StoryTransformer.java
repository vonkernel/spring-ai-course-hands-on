package hn.chatbot.service.setup;

import hn.chatbot.ai.ArticleBodyExtractor;
import hn.chatbot.ai.ArticleChunker;
import hn.chatbot.ai.CommentModerator;
import hn.chatbot.ai.HarmfulPhraseDetector;
import hn.chatbot.ai.IssueAnalysis;
import hn.chatbot.ai.IssueAnalyzer;
import hn.chatbot.ai.MaskingResult;
import hn.chatbot.ai.ModerationVerdict;
import hn.chatbot.domain.Article;
import hn.chatbot.domain.ArticleStatus;
import hn.chatbot.domain.Comment;
import hn.chatbot.domain.Story;
import hn.chatbot.service.setup.model.ArticleCheck;
import hn.chatbot.service.setup.model.CollectedComment;
import hn.chatbot.service.setup.model.CollectedStory;
import hn.chatbot.service.setup.model.LogKind;
import hn.chatbot.service.setup.model.LogState;
import hn.chatbot.service.setup.model.TransformedStory;
import hn.chatbot.service.setup.port.ArticleSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 인덱싱 파이프라인의 변환(T). 3단계 댓글 검열부터 7단계 청킹까지 맡는다. 완성본이다.
 *
 * 실습에서 만든 부품을 순서대로 부른다.
 *
 * - 3 댓글 검열: CommentModerator 에 걸린 댓글만 HarmfulPhraseDetector 구간을 PipelinePolicy.MASK 로 치환
 * - 4 기계적 정제: ArticleSource.fetch 결과를 PipelinePolicy.check 로 판정. passed() 가 아니면 제외
 * - 5 본문 추출: ArticleBodyExtractor. 찾지 못하면 제외
 * - 6 분석: IssueAnalyzer 에 최상위 댓글 PipelinePolicy.TOP_COMMENTS 개를 함께 넘긴다. 부적합이면 제외
 * - 7 청킹: ArticleChunker. 돌려준 순서가 곧 seq
 *
 * 저장하지 않는다. 결과를 TransformedStory 로 모아 돌려주고, 저장은 StoryLoader 가 한 번에 한다.
 * 그래서 중간에 부품이 예외를 던지면 그 스토리는 아무것도 저장되지 않고 다음 실행에서 처음부터 다시 처리된다.
 */
@Component
public class StoryTransformer {

    private final CommentModerator commentModerator;
    private final HarmfulPhraseDetector harmfulPhraseDetector;
    private final ArticleSource articleSource;
    private final ArticleBodyExtractor articleBodyExtractor;
    private final IssueAnalyzer issueAnalyzer;
    private final ArticleChunker articleChunker;

    public StoryTransformer(CommentModerator commentModerator, HarmfulPhraseDetector harmfulPhraseDetector,
                            ArticleSource articleSource, ArticleBodyExtractor articleBodyExtractor,
                            IssueAnalyzer issueAnalyzer, ArticleChunker articleChunker) {
        this.commentModerator = commentModerator;
        this.harmfulPhraseDetector = harmfulPhraseDetector;
        this.articleSource = articleSource;
        this.articleBodyExtractor = articleBodyExtractor;
        this.issueAnalyzer = issueAnalyzer;
        this.articleChunker = articleChunker;
    }

    public TransformedStory transform(CollectedStory collected, StageTracker tracker) {
        Story story = new Story(collected.id(), collected.title(), collected.url(), collected.author(),
                collected.score(), collected.descendants(), collected.text(), collected.postedAt());

        List<Comment> comments = collected.comments().stream()
                .map(comment -> moderate(collected.id(), comment))
                .toList();
        tracker.passed(3);

        ArticleCheck check = PipelinePolicy.check(articleSource.fetch(collected.url()));
        if (!check.passed()) {
            tracker.log(LogKind.EXCLUDE, collected.id(), collected.title(), check.status().name(), LogState.WARN);
            return excluded(story, comments, article(collected.id(), check.status(), check, null));
        }
        tracker.passed(4);

        Optional<String> body = articleBodyExtractor.extract(collected.title(), check.text());
        if (body.isEmpty()) {
            tracker.log(LogKind.EXCLUDE, collected.id(), collected.title(), "본문 없음", LogState.WARN);
            return excluded(story, comments, article(collected.id(), ArticleStatus.NO_BODY, check, null));
        }
        Article article = article(collected.id(), ArticleStatus.PASS, check, body.get());
        tracker.passed(5);
        tracker.log(LogKind.EXTRACT, collected.id(), collected.title(), body.get().length() + "자", LogState.OK);

        IssueAnalysis analysis = issueAnalyzer.analyze(collected.title(), body.get(), topComments(comments));
        tracker.passed(6);
        tracker.log(LogKind.ANALYZE, collected.id(), collected.title(), analysis.category(), LogState.OK);
        if (!analysis.suitable()) {
            return new TransformedStory(story, comments, article, analysis, List.of());
        }

        List<String> chunks = articleChunker.chunk(body.get());
        tracker.passed(7);
        tracker.log(LogKind.CHUNK, collected.id(), collected.title(), chunks.size() + "개", LogState.OK);

        return new TransformedStory(story, comments, article, analysis, chunks);
    }

    /** 3단계 — 걸린 댓글만 유해 구간을 치환한다. */
    private Comment moderate(long storyId, CollectedComment comment) {
        ModerationVerdict verdict = commentModerator.inspect(comment.text());
        String text = comment.text();
        if (verdict.flagged()) {
            MaskingResult masking = harmfulPhraseDetector.detect(text);
            for (String phrase : masking.harmfulPhrases()) {
                text = text.replace(phrase, PipelinePolicy.MASK);
            }
        }
        return new Comment(comment.id(), storyId, comment.parentId(), comment.author(),
                text, comment.depth(), comment.postedAt(), verdict.flagged());
    }

    private static List<String> topComments(List<Comment> comments) {
        return comments.stream()
                .filter(comment -> comment.getDepth() == 1)
                .limit(PipelinePolicy.TOP_COMMENTS)
                .map(Comment::getText)
                .toList();
    }

    private static Article article(long storyId, ArticleStatus status, ArticleCheck check, String body) {
        return new Article(storyId, status, check.text(), check.length(), body);
    }

    private static TransformedStory excluded(Story story, List<Comment> comments, Article article) {
        return new TransformedStory(story, comments, article, null, List.of());
    }
}
