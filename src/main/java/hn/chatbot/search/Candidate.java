package hn.chatbot.search;

import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 적합성 판단 이전의 후보.
 *
 * 제목과 요약은 StoryIndexQuery.hydrate 가 채우고, matchedPlans 는 SearchService 가 채운다.
 *
 * SearchService 는 Spring AI 의 DocumentRetriever 라서 후보를 Document 로 돌려준다.
 * 둘 사이의 변환은 toDocument · from 이 맡는다. 완성본이다.
 * Document 의 id 는 storyId 문자열, text 는 요약(없으면 제목)이다.
 */
public record Candidate(long storyId, String title, String summary, List<Integer> matchedPlans) {

    public static final String STORY_ID = "storyId";
    public static final String TITLE = "title";
    public static final String MATCHED_PLANS = "matchedPlans";

    public Document toDocument() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(STORY_ID, storyId);
        metadata.put(TITLE, title == null ? "" : title);
        metadata.put(MATCHED_PLANS, matchedPlans == null ? List.of() : List.copyOf(matchedPlans));
        String text = summary != null && !summary.isBlank() ? summary : metadata.get(TITLE).toString();
        return Document.builder()
                .id(String.valueOf(storyId))
                .text(text.isBlank() ? String.valueOf(storyId) : text)
                .metadata(metadata)
                .build();
    }

    @SuppressWarnings("unchecked")
    public static Candidate from(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        long storyId = ((Number) metadata.get(STORY_ID)).longValue();
        Object plans = metadata.get(MATCHED_PLANS);
        List<Integer> matchedPlans = plans instanceof List<?> list
                ? ((List<Number>) list).stream().map(Number::intValue).toList()
                : List.of();
        return new Candidate(storyId, (String) metadata.get(TITLE), document.getText(), matchedPlans);
    }
}
