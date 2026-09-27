package hn.chatbot.service.chat;

import hn.chatbot.search.SearchService;
import hn.chatbot.service.chat.port.ChatQuery;
import hn.chatbot.service.topic.TopicService;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicDistribution;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 모델이 호출하는 도구 7종. 수강생이 채운다.
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
 * 두 분류 모두 폴백으로 런타임에 새 값이 생기므로 「목록에 없으면 listTopics 로 확인」도 적는다.
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
 * 도구 호출의 중간 메시지는 대화 기억에 남지 않으므로, 목록을 돌려주는 도구는
 * 결과를 직접 기록한다. 문구는 SearchRecord 가 만든다.
 */
@Component
public class IssueTools {

    /** ToolContext 키. 모델이 다듬기 전의 사용자 원문. 도구를 등록하는 쪽이 넣고 searchIssues 가 읽는다. */
    public static final String USER_QUESTION = "userQuestion";

    private final SearchService searchService;
    private final RelevancePostProcessor relevancePostProcessor;
    private final TopicService topicService;
    private final ChatQuery chatQuery;
    private final ChatMemory chatMemory;

    public IssueTools(SearchService searchService, RelevancePostProcessor relevancePostProcessor,
                      TopicService topicService, ChatQuery chatQuery, ChatMemory chatMemory) {
        this.searchService = searchService;
        this.relevancePostProcessor = relevancePostProcessor;
        this.topicService = topicService;
        this.chatQuery = chatQuery;
        this.chatMemory = chatMemory;
    }

    /**
     * 질의와 의미가 관련된 기술 이슈를 찾아 근거를 돌려준다. 개수를 세거나 목록을 나열할 때는 쓰지 않는다.
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
     * 5 ChatTurn.from(ctx).publish 로 화면에 사건을 보낸다
     * 6 ctx 의 conversationId 로 SearchRecord.of 문구를 AssistantMessage 로 기억에 기록한다
     * 7 SearchEvidence 를 돌려준다. 모델이 이것으로 답을 쓴다
     *
     * 2 · 3 은 RagChatServiceShell 이 RetrievalAugmentationAdvisor 에 넘기는 부품 그대로다.
     * 그때는 Advisor 가 질문마다 불렀고, 이제는 모델이 이 도구를 골랐을 때만 부른다.
     */
    public SearchEvidence searchIssues(String query, String techField, String category, ToolContext ctx) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 기술 분야나 원문 타입별 이슈 건수를 센다. 벡터 검색으로는 할 수 없는 일이다.
     *
     * techField 와 category 의 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다.
     *
     * 두 인자 모두 선택이다. TopicService.count 에 그대로 넘기면 비운 축은 거르지 않는다.
     */
    public int countStories(String techField, String category) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 특정 기술 분야의 이슈를 나열한다.
     *
     * techField 는 선택이다. 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다.
     * 비면 모든 분야를 나열한다. 빈 문자열을 그대로 넘기면 「이름이 빈 분야」를 찾게 되므로
     * 없는 값으로 바꿔 넘긴다.
     *
     * sortBy 는 SCORE(점수순) 또는 RECENT(최신순)다. 비었거나 그 밖의 값이면 SCORE 로 다룬다.
     *
     * limit 은 비었거나 0 이하면 20, 50 을 넘으면 50 으로 자른다.
     *
     * 결과를 SearchRecord.ofStories 문구로 대화 기억에 기록한다.
     */
    public List<StorySummary> listStories(String techField, String sortBy, Integer limit, ToolContext ctx) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 스토리 하나의 요약 · 커뮤니티 반응 · 실무 시사점을 가져온다.
     *
     * storyId 를 이미 받아 호출되므로 대화 기억에 따로 남기지 않아도 된다.
     */
    public StoryDetail getStoryDetail(long storyId) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 스토리의 실제 댓글을 가져온다. "방금 그 이슈 댓글 보여줘" 같은 후속 질문이 여기로 온다.
     *
     * 돌려주는 text 는 검열을 거쳐 마스킹된 값이다.
     *
     * limit 은 비었거나 0 이하면 5, 10 을 넘으면 10 으로 자른다.
     */
    public List<CommentView> getComments(long storyId, Integer limit) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 수집된 이슈가 어떤 기술 분야와 원문 타입으로 나뉘는지 분포를 가져온다.
     *
     * TopicService 를 그대로 호출한다. 주제 탐색 탭과 모델이 같은 데이터를 본다.
     */
    public TopicDistribution listTopics() {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 특정 기술 분야 전반의 논의 흐름을 요약한다. 개별 이슈를 찾을 때는 쓰지 않는다.
     *
     * techField 의 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다. 빈 문자열은
     * 없는 값으로 바꿔 넘긴다.
     *
     * TopicService.summarize 에 위임한다. 그 안에서 TopicSummarizer 가 LLM 을 호출한다.
     */
    public String summarizeTopic(String techField) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
