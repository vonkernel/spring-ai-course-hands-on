package hn.chatbot.service.chat;

import hn.chatbot.service.topic.TopicService;
import hn.chatbot.service.topic.model.StorySummary;
import hn.chatbot.service.topic.model.TopicCount;
import org.springframework.ai.chat.model.ToolContext;
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

    private final TopicService topicService;

    public TopicTools(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * 기술 분야 목록과 분야별 스토리 건수를 가져온다.
     *
     * TopicService.distribution 의 techFields 를 돌려준다. 주제 탐색 탭과 모델이 같은 분야 목록을 본다.
     */
    public List<TopicCount> listTopics() {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
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
    public List<StorySummary> listStories(String techField, String sortBy, Integer limit, ToolContext ctx) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 기술 분야의 스토리 건수를 센다. 벡터 검색으로는 할 수 없는 일이다.
     *
     * techField 는 선택이다. 허용값은 클래스 Javadoc 에 있고 설명 문구에 적는다.
     * 비면 모든 분야의 건수다. TopicService.count 에 넘긴다.
     */
    public int countStories(String techField) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    /**
     * 특정 기술 분야 전반의 논의 흐름을 요약한다. 개별 스토리를 찾을 때는 쓰지 않는다.
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
