package hn.chatbot.service.chat;

import hn.chatbot.service.topic.TopicService;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicCount;
import hn.chatbot.service.topic.model.TopicStories;
import hn.chatbot.service.topic.port.TopicQuery.StorySort;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 모델이 호출하는 주제 도구 4종. 주제는 기술 분야(techField)다.
 * 분야 목록, 분야별 스토리 목록과 건수, 분야 전반의 논의 흐름 요약을 돌려준다.
 * 질의로 스토리를 찾는 일은 StoryTools 가 맡는다.
 *
 * 도구 등록(@Tool · @ToolParam)과 인자 검증이 구현 대상이다. 설명 문구가 곧 모델의
 * 선택 기준이므로 언제 쓰고 언제 쓰지 않는지를 적는다.
 *
 * 모델이 채우는 인자는 비어 있거나 허용 범위를 벗어날 수 있다. 빈 문자열은 없는 값으로
 * 다루고, limit 은 비면 기본값, 크면 상한으로 자른다.
 *
 * techField 는 ENUM 이 아니라 TEXT 다. 대소문자까지 정확히 일치해야 걸리는데
 * 모델은 허용값을 모른다. 목록을 @ToolParam 설명에 적지 않으면 「하드웨어」나 hardware 를
 * 넣고, 예외 없이 0건이 돌아와 그 결과를 근거로 그럴듯한 오답이 만들어진다.
 * 폴백으로 런타임에 새 값이 생기므로 「목록에 없으면 listTopics 로 확인」도 적는다.
 *
 * techField 값: AI_LLM · SECURITY_PRIVACY · OPEN_SOURCE · INFRASTRUCTURE_ENTERPRISE ·
 * PLATFORM_POLICY · DEV_CULTURE_PRACTICE · HARDWARE · MOBILITY · NON_TECHNICAL
 *
 * 반환 타입에 web/dto 를 쓰지 않는다. 도구 반환값은 JSON 으로 직렬화돼
 * 모델에게 들어간다. 화면용 한글 표시 이름이 섞이면 토큰만 쓴다.
 *
 * listStories 는 ToolContext 를 받는다. 이 도구를 등록한 호출은 모두 .toolContext(...) 를 채워야 한다.
 * 결과는 ChatTurn 에 넘긴다. 기억 기록은 ChatTurn 이 맡는다.
 */
@Component
public class TopicTools {

    /** 모델이 techField 에 넣을 수 있는 값. 폴백으로 새 값이 생기므로 listTopics 안내를 함께 둔다. */
    static final String TECH_FIELDS = "허용값: AI_LLM, SECURITY_PRIVACY, OPEN_SOURCE, "
            + "INFRASTRUCTURE_ENTERPRISE, PLATFORM_POLICY, DEV_CULTURE_PRACTICE, HARDWARE, "
            + "MOBILITY, NON_TECHNICAL. 이 목록에 없는 분야를 찾을 때는 listTopics 로 실제 값을 먼저 확인한다";

    private static final int LIST_DEFAULT_LIMIT = 20;
    private static final int LIST_MAX_LIMIT = 50;

    private final TopicService topicService;

    public TopicTools(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * 기술 분야 목록과 분야별 스토리 건수를 가져온다.
     *
     * TopicService.distribution 의 techFields 를 돌려준다. 주제 탐색 탭과 모델이 같은 분야 목록을 본다.
     */
    @Tool(description = "기술 분야 목록과 분야별 스토리 건수를 가져온다. "
            + "'어떤 기술 분야들이 있어' 처럼 분야 구성을 물을 때 쓴다.")
    public List<TopicCount> listTopics() {
        return topicService.distribution().techFields();
    }

    /**
     * 특정 기술 분야의 스토리를 나열한다.
     *
     * techField 는 선택이다. 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다.
     * 비면 모든 분야를 나열한다. 빈 문자열을 그대로 넘기면 「이름이 빈 분야」를 찾게 되므로
     * 없는 값으로 바꿔 넘긴다.
     *
     * sortBy 는 SCORE(점수순) 또는 RECENT(최신순)다. 비었거나 그 밖의 값이면 SCORE 로 다룬다.
     *
     * limit 은 비었거나 0 이하면 20, 50 을 넘으면 50 으로 자른다.
     *
     * 결과를 ChatTurn.from(ctx).publishStories 로 넘긴다. sortBy 는 실제로 쓴 정렬 이름을 넘긴다.
     */
    @Tool(description = "특정 기술 분야의 스토리 목록을 점수순 또는 최신순으로 나열한다. "
            + "'AI 쪽 이슈 점수순으로 보여줘' 처럼 목록을 물을 때 쓴다.")
    public List<StorySummary> listStories(
            @ToolParam(required = false, description = "나열할 기술 분야. 비우면 모든 분야를 나열한다. " + TECH_FIELDS) String techField,
            @ToolParam(required = false, description = "정렬 기준. SCORE(점수순) 또는 RECENT(최신순). "
                    + "비우거나 알 수 없는 값이면 SCORE 를 쓴다") String sortBy,
            @ToolParam(required = false, description = "최대 개수. 비우면 기본값을 쓰고, 상한을 넘으면 상한으로 자른다") Integer limit,
            ToolContext ctx) {
        StorySort sort = parseSort(sortBy);
        int effectiveLimit = limit == null || limit <= 0 ? LIST_DEFAULT_LIMIT : Math.min(limit, LIST_MAX_LIMIT);

        TopicStories result = topicService.stories(blankToNull(techField), sort, effectiveLimit);
        List<StorySummary> stories = result.stories();

        ChatTurn.from(ctx).publishStories(techField, sort.name(), stories);
        return stories;
    }

    /**
     * 기술 분야의 스토리 건수를 센다. 벡터 검색으로는 할 수 없는 일이다.
     *
     * techField 는 선택이다. 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다.
     * 비면 모든 분야의 건수다. TopicService.count 에 넘긴다.
     */
    @Tool(description = "기술 분야의 스토리 건수를 센다. '이슈가 몇 건이야' 처럼 집계를 물을 때 쓴다. "
            + "개별 스토리를 찾을 때는 쓰지 않는다.")
    public int countStories(
            @ToolParam(required = false, description = "건수를 셀 기술 분야. 비우면 모든 분야를 센다. " + TECH_FIELDS) String techField) {
        return topicService.count(blankToNull(techField));
    }

    /**
     * 특정 기술 분야 전반의 논의 흐름을 요약한다. 개별 스토리를 찾을 때는 쓰지 않는다.
     *
     * techField 의 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다. 빈 문자열은
     * 없는 값으로 바꿔 넘긴다.
     *
     * TopicService.summarize 에 위임한다. 그 안에서 TopicSummarizer 가 LLM 을 호출한다.
     */
    @Tool(description = "특정 기술 분야 전반의 논의 흐름을 요약한다. 'AI 쪽 논의 흐름 정리해줘' 처럼 분야 전체를 물을 때 쓴다. "
            + "개별 스토리를 찾을 때는 쓰지 않는다(그때는 searchStories 를 쓴다).")
    public String summarizeTopic(@ToolParam(description = "요약할 기술 분야. " + TECH_FIELDS) String techField) {
        return topicService.summarize(blankToNull(techField));
    }

    private static StorySort parseSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return StorySort.SCORE;
        }
        try {
            return StorySort.valueOf(sortBy.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return StorySort.SCORE;
        }
    }

    /** 모델은 없는 값을 빈 문자열로 채우기도 한다. 그대로 넘기면 「이름이 빈 분야」를 찾게 된다. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
