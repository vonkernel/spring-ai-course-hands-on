package hn.chatbot.ai;

import java.util.List;

/**
 * 기술 분야 하나의 논의 흐름을 산문으로 요약한다.
 * TopicService.summarize 가 위임하고, summarizeTopic 도구가 그것을 부른다.
 *
 * 스토리마다 제목 · 요약 · 커뮤니티 반응이 들어온다. 스토리를 하나씩 나열하지 말고
 * 여러 스토리에 공통으로 흐르는 쟁점을 묶어 요약한다.
 * 스토리마다 커뮤니티 반응이 갈리면 그 차이도 흐름의 일부로 쓴다. 입력에 없는 내용을 지어내지 않는다.
 *
 * 요약은 한국어로 쓰게 프롬프트에 적는다.
 */
public interface TopicSummarizer {

    String summarize(String techField, List<AnalysisTarget> targets);
}
