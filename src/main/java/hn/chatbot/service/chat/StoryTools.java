package hn.chatbot.service.chat;

import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchService;
import hn.chatbot.service.chat.port.ChatQuery;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 모델이 호출하는 스토리 도구 3종. 질의로 스토리를 찾고, 스토리 한 건의 상세와 댓글을 가져온다.
 * 분야 단위의 목록 · 건수 · 요약은 TopicTools 가 맡는다.
 *
 * 도구 등록(@Tool · @ToolParam)과 인자 검증이 구현 대상이다. 설명 문구가 곧 모델의
 * 선택 기준이므로 언제 쓰고 언제 쓰지 않는지를 적는다.
 *
 * 모델이 채우는 인자는 비어 있거나 허용 범위를 벗어날 수 있다. 빈 문자열은 없는 값으로
 * 다루고, limit 은 비면 기본값, 크면 상한으로 자른다.
 *
 * techField 와 category 는 ENUM 이 아니라 TEXT 다. 대소문자까지 정확히 일치해야 걸리는데
 * 모델은 허용값을 모른다. 목록을 @ToolParam 설명에 적지 않으면 「하드웨어」나 hardware 를
 * 넣고, 예외 없이 0건이 돌아와 그 결과를 근거로 그럴듯한 오답이 만들어진다.
 * techField 는 폴백으로 런타임에 새 값이 생기므로 「목록에 없으면 listTopics 로 확인」도 적는다.
 *
 * techField 값: AI_LLM · SECURITY_PRIVACY · OPEN_SOURCE · INFRASTRUCTURE_ENTERPRISE ·
 * PLATFORM_POLICY · DEV_CULTURE_PRACTICE · HARDWARE · MOBILITY · NON_TECHNICAL
 *
 * category 값: OFFICIAL_ANNOUNCEMENT · RELEASE_NOTES · NEWS_REPORT · OPINION_ESSAY ·
 * TECHNICAL_DEEP_DIVE · RESEARCH_PAPER · SHOW_HN_PROJECT · ASK_TELL_HN · PRODUCT_MARKETING
 *
 * 반환 타입에 web/dto 를 쓰지 않는다. 도구 반환값은 JSON 으로 직렬화돼
 * 모델에게 들어간다. 화면용 한글 표시 이름이 섞이면 토큰만 쓴다.
 *
 * ToolContext 를 받는 도구는 모든 호출이 .toolContext(...) 를 채워야 한다.
 * 빠뜨리면 모델을 호출하기도 전에 IllegalArgumentException 이 난다.
 * searchStories 는 결과를 ChatTurn 에 넘긴다. 화면 전송과 기억 기록은 ChatTurn 이 맡는다.
 */
@Component
public class StoryTools {

    /** ToolContext 키. 모델이 다듬기 전의 사용자 원문. 도구를 등록하는 쪽이 넣고 searchStories 가 읽는다. */
    public static final String USER_QUESTION = "userQuestion";

    /** 모델이 techField 에 넣을 수 있는 값. 폴백으로 새 값이 생기므로 listTopics 안내를 함께 둔다. */
    static final String TECH_FIELDS = "허용값: AI_LLM, SECURITY_PRIVACY, OPEN_SOURCE, "
            + "INFRASTRUCTURE_ENTERPRISE, PLATFORM_POLICY, DEV_CULTURE_PRACTICE, HARDWARE, "
            + "MOBILITY, NON_TECHNICAL. 이 목록에 없는 분야를 찾을 때는 listTopics 로 실제 값을 먼저 확인한다";

    /** 모델이 category 에 넣을 수 있는 값. */
    static final String CATEGORIES = "허용값: OFFICIAL_ANNOUNCEMENT, RELEASE_NOTES, NEWS_REPORT, "
            + "OPINION_ESSAY, TECHNICAL_DEEP_DIVE, RESEARCH_PAPER, SHOW_HN_PROJECT, ASK_TELL_HN, "
            + "PRODUCT_MARKETING";

    private static final int COMMENTS_DEFAULT_LIMIT = 5;
    private static final int COMMENTS_MAX_LIMIT = 10;

    private final SearchService searchService;
    private final RelevancePostProcessor relevancePostProcessor;
    private final ChatQuery chatQuery;

    public StoryTools(SearchService searchService, RelevancePostProcessor relevancePostProcessor, ChatQuery chatQuery) {
        this.searchService = searchService;
        this.relevancePostProcessor = relevancePostProcessor;
        this.chatQuery = chatQuery;
    }

    /**
     * 질의와 의미가 관련된 스토리를 찾아 근거를 돌려준다. 개수를 세거나 분야별 목록을 나열할 때는 쓰지 않는다.
     *
     * techField · category 는 선택이다. 모델이 질의를 보고 채운다("공식 발표만" → category).
     * 비우면 그 축으로 거르지 않는다. 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다.
     *
     * 흐름
     *
     * 1 SearchContext.query 로 Query 를 만든다. 검색어는 query 인자, 필터는 techField · category,
     *   사용자 원문은 ctx 의 USER_QUESTION 이다. 원문은 계획 1 이 쓴다.
     *   모델이 다듬은 검색어에는 사용자가 쓴 키워드가 그대로 남지 않기 때문이다
     * 2 SearchService.retrieve 로 후보를 찾는다. 계획별 요약은 Query 의 컨텍스트에 기록된다
     * 3 RelevancePostProcessor.process 로 근거를 고른다
     * 4 SearchEvidence.of(Query, 근거) 로 합친다
     * 5 ChatTurn.from(ctx).publish(Query, SearchEvidence) 로 결과를 넘긴다. 화면 사건과 기억 기록은 ChatTurn 이 맡는다
     * 6 SearchEvidence 를 돌려준다. 모델이 이것으로 답을 쓴다
     *
     * 2 · 3 은 RagChatServiceShell 이 RetrievalAugmentationAdvisor 에 넘기는 SearchService · RelevancePostProcessor 그대로다.
     * 그때는 Advisor 가 질문마다 불렀고, 이제는 모델이 이 도구를 골랐을 때만 부른다.
     */
    @Tool(description = "질의와 의미가 관련된 스토리를 찾아 근거를 돌려준다. "
            + "'코딩 에이전트 한계가 뭐야' 처럼 특정 내용을 묻는 질문에 쓴다. "
            + "개수를 세거나 분야별 목록을 나열할 때는 쓰지 않는다(그때는 countStories · listStories 를 쓴다).")
    public SearchEvidence searchStories(
            @ToolParam(description = "검색할 질문. 사용자의 질문을 그대로 또는 검색에 알맞게 다듬어 넣는다") String query,
            @ToolParam(required = false, description = "찾을 기술 분야. 비우면 모든 분야에서 찾는다. " + TECH_FIELDS) String techField,
            @ToolParam(required = false, description = "찾을 원문 타입. 예: 공식 발표만 찾을 때. 비우면 모든 원문 타입에서 찾는다. " + CATEGORIES) String category,
            ToolContext ctx) {
        Query search = SearchContext.query(query, (String) ctx.getContext().get(USER_QUESTION),
                techField, category);
        List<Document> candidates = searchService.retrieve(search);
        List<Document> judged = relevancePostProcessor.process(search, candidates);
        SearchEvidence evidence = SearchEvidence.of(search, judged);

        ChatTurn.from(ctx).publish(search, evidence);
        return evidence;
    }

    /**
     * 스토리 하나의 요약 · 커뮤니티 반응 · 실무 시사점을 가져온다.
     *
     * storyId 를 이미 받아 호출되므로 대화 기억에 따로 남기지 않아도 된다.
     */
    @Tool(description = "특정 스토리 하나의 요약 · 커뮤니티 반응 · 실무 시사점을 가져온다. storyId 를 이미 알고 있을 때 쓴다.")
    public StoryDetail getStoryDetail(@ToolParam(description = "조회할 스토리 id") long storyId) {
        return chatQuery.storyDetail(storyId)
                .orElseThrow(() -> new IllegalArgumentException("story not found: " + storyId));
    }

    /**
     * 스토리의 실제 댓글을 가져온다. "방금 그 이슈 댓글 보여줘" 같은 후속 질문이 여기로 온다.
     *
     * 돌려주는 text 는 검열을 거쳐 마스킹된 값이다.
     *
     * limit 은 비었거나 0 이하면 5, 10 을 넘으면 10 으로 자른다.
     */
    @Tool(description = "특정 스토리의 실제 댓글을 가져온다. '방금 그 이슈 댓글 보여줘' 처럼 댓글 원문을 물을 때 쓴다.")
    public List<CommentView> getComments(
            @ToolParam(description = "댓글을 가져올 스토리 id") long storyId,
            @ToolParam(required = false, description = "최대 개수. 비우면 기본값을 쓰고, 상한을 넘으면 상한으로 자른다") Integer limit) {
        // 비거나 말이 안 되는 값은 기본값으로 되돌린다. 큰 값만 상한으로 자른다.
        int effectiveLimit = limit == null || limit <= 0 ? COMMENTS_DEFAULT_LIMIT : Math.min(limit, COMMENTS_MAX_LIMIT);
        return chatQuery.comments(storyId, effectiveLimit);
    }
}
