package hn.chatbot.service.chat;

import hn.chatbot.service.topic.model.StorySummary;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 대화 기억에 남길 검색 기록 문구를 만든다. 완성본이다.
 *
 * 도구 호출의 중간 메시지는 기억에 남지 않으므로, 목록을 돌려주는 도구가 결과를 ChatTurn 에 넘기면
 * ChatTurn 이 이 문구를 AssistantMessage 로 기록한다. SystemMessage 로 기록하지 않는다.
 * MessageWindowChatMemory 는 새 SystemMessage 가 들어오면 이전 것을 모두 지운다.
 *
 * 번호는 화면의 근거 카드 순서와 같다. "두 번째 이슈"가 화면과 어긋나지 않게 한다.
 *
 * 문구는 영어다. 한국어 기록이 쌓이면 모델이 영어 질문에도 한국어로 답하고,
 * 기록의 머리말 형식을 답변에 흉내 내는 경향이 있었다.
 */
public final class SearchRecord {

    private SearchRecord() {
    }

    /** searchStories 용. 검색 조건, 계획별 건수, 근거 목록. */
    public static String of(String query, String techField, String category, SearchEvidence evidence) {
        String plans = evidence.plans().stream()
                .map(p -> String.valueOf(p.hits()))
                .collect(Collectors.joining("·"));
        String items = evidence.evidence().stream()
                .map(i -> "%d. %d %s".formatted(i.rank(), i.storyId(), i.title()))
                .collect(Collectors.joining("\n"));
        return "[search record] query=\"%s\", techField=%s, category=%s / hits per plan %s\n%s".formatted(
                query, orNone(techField), orNone(category), plans,
                items.isEmpty() ? "(no evidence)" : items);
    }

    /** listStories 용. 조건과 목록 순서. */
    public static String ofStories(String techField, String sortBy, List<StorySummary> stories) {
        String items = IntStream.range(0, stories.size())
                .mapToObj(i -> "%d. %d %s".formatted(i + 1, stories.get(i).storyId(), stories.get(i).title()))
                .collect(Collectors.joining("\n"));
        return "[list record] techField=%s, sortBy=%s\n%s".formatted(
                orNone(techField), sortBy, items.isEmpty() ? "(no results)" : items);
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
