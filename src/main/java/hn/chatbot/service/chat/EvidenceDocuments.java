package hn.chatbot.service.chat;

import hn.chatbot.ai.AnalysisTarget;
import hn.chatbot.ai.RelevantStory;
import hn.chatbot.search.Candidate;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 후보 Document 와 근거 Document 사이를 잇는 도우미. 완성본이다.
 *
 * RelevancePostProcessor 가 쓴다. 판단 입력을 만들고, 판단 결과와 조회한 상세를 합쳐
 * 근거 Document 를 만든다. 근거 Document 는 후보의 metadata 에 근거 카드 필드를 더한 것이다.
 * SearchEvidence 가 그것을 다시 읽어 화면 사건과 모델에 넘길 형태로 옮긴다.
 *
 * 여기서 대목을 다시 검사한다. 모델이 원문을 바꿔 쓴 대목이나 후보에 없는 storyId 는 버린다.
 */
public final class EvidenceDocuments {

    static final String RANK = "rank";
    static final String URL = "url";
    static final String CATEGORY = "category";
    static final String SUMMARY = "summary";
    static final String COMMUNITY_REACTION = "communityReaction";
    static final String PRACTICAL_IMPLICATION = "practicalImplication";
    static final String PASSAGE = "passage";

    static final int PASSAGE_MAX = 300;

    private EvidenceDocuments() {
    }

    /** 후보들의 storyId. 원문 앞부분을 조회할 때 쓴다. */
    public static List<Long> storyIds(List<Document> candidates) {
        return candidates.stream().map(d -> Candidate.from(d).storyId()).toList();
    }

    /** 판단 입력. 원문 앞부분이 있는 후보만 넘긴다. */
    public static List<AnalysisTarget> targets(List<Document> candidates, Map<Long, String> excerpts) {
        return candidates.stream()
                .map(Candidate::from)
                .filter(c -> excerpts.containsKey(c.storyId()))
                .map(c -> AnalysisTarget.forJudge(c.storyId(), c.title(), c.summary(), excerpts.get(c.storyId())))
                .toList();
    }

    /**
     * 판단 순서대로 근거 Document 를 만든다. rank 는 1 부터다.
     * 후보나 상세가 없는 storyId, 이미 나온 storyId 는 건너뛴다.
     */
    public static List<Document> evidence(List<Document> candidates, List<RelevantStory> judged,
                                          List<StoryDetail> details, Map<Long, String> excerpts) {
        Map<Long, Document> candidateById = candidates.stream()
                .collect(Collectors.toMap(d -> Candidate.from(d).storyId(), Function.identity(), (a, b) -> a));
        Map<Long, StoryDetail> detailById = details.stream()
                .collect(Collectors.toMap(StoryDetail::storyId, Function.identity(), (a, b) -> a));

        List<Document> evidence = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        for (RelevantStory story : judged) {
            Document candidate = candidateById.get(story.storyId());
            StoryDetail detail = detailById.get(story.storyId());
            if (candidate == null || detail == null || seen.contains(story.storyId())) {
                continue;
            }
            seen.add(story.storyId());

            Map<String, Object> metadata = new HashMap<>(candidate.getMetadata());
            metadata.put(RANK, evidence.size() + 1);
            putIfPresent(metadata, Candidate.TITLE, detail.title());
            putIfPresent(metadata, URL, detail.url());
            putIfPresent(metadata, CATEGORY, detail.category());
            putIfPresent(metadata, SUMMARY, detail.summary());
            putIfPresent(metadata, COMMUNITY_REACTION, detail.communityReaction());
            putIfPresent(metadata, PRACTICAL_IMPLICATION, detail.practicalImplication());
            putIfPresent(metadata, PASSAGE, verifiedPassage(story.passage(), excerpts.get(story.storyId())));

            evidence.add(Document.builder()
                    .id(candidate.getId())
                    .text(candidate.getText())
                    .metadata(metadata)
                    .build());
        }
        return evidence;
    }

    /** 근거 Document 를 근거 한 건으로 읽는다. */
    @SuppressWarnings("unchecked")
    static SearchEvidence.Item item(Document evidence) {
        Map<String, Object> m = evidence.getMetadata();
        Candidate candidate = Candidate.from(evidence);
        return new SearchEvidence.Item(((Number) m.get(RANK)).intValue(), candidate.storyId(),
                (String) m.get(Candidate.TITLE), (String) m.get(URL), candidate.matchedPlans(),
                (String) m.get(CATEGORY), (String) m.get(SUMMARY), (String) m.get(COMMUNITY_REACTION),
                (String) m.get(PRACTICAL_IMPLICATION), (String) m.get(PASSAGE));
    }

    /** 원문에 그대로 있는 대목만 남긴다. 공백 차이는 무시한다. */
    static String verifiedPassage(String passage, String excerpt) {
        if (passage == null || passage.isBlank() || excerpt == null) {
            return null;
        }
        String clean = passage.strip();
        if (!squash(excerpt).contains(squash(clean))) {
            return null;
        }
        return clean.length() <= PASSAGE_MAX ? clean : clean.substring(0, PASSAGE_MAX);
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", " ");
    }

    /** Document 의 metadata 에는 null 을 넣지 않는다. 없는 값은 키째 뺀다. */
    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
