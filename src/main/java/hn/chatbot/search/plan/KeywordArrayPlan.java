package hn.chatbot.search.plan;

import hn.chatbot.search.PlanConditions;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchPlan;
import hn.chatbot.search.port.StoryIndexQuery;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 검색 계획 1 — 키워드 완전 일치.
 *
 * 배열 겹침(&&)이라 형태소 분석이 필요 없다. 질문에 저장된 키워드(고유명사 · 제품명 · 기술 용어)가
 * 들어 있을 때 가장 정확하다.
 *
 * 완성본이다. 두 가지를 지킨다.
 *
 * 1 모델이 다듬은 검색어가 아니라 사용자 원문(SearchContext.userQuestion)을 본다.
 *   모델은 키워드 하나를 문장으로 늘리거나 번역하거나 조사를 붙여, 사용자가 쓴 표기를 바꾼다.
 * 2 질문을 공백으로 잘라 맞추지 않고, 저장된 키워드가 질문 안에 들어 있는지 찾는다.
 *   저장된 키워드에는 digital sovereignty 처럼 두 단어 이상인 것이 많아 공백으로 자르면
 *   영영 걸리지 않는다. 대소문자는 가리지 않는다. 키워드 앞뒤가 영문자 · 숫자가 아니어야
 *   맞은 것으로 본다. 그래서 「VMware의」는 맞고, algorithm 안의 lg 는 맞지 않는다.
 *
 * 찾아낸 키워드는 저장된 표기 그대로 조회에 넘기고 실행 조건에도 그대로 적는다.
 *
 * 벡터 계획(3 · 4)을 구현할 때 결과와 실행 조건을 함께 돌려주는 형태의 참고가 된다.
 */
@Component
public class KeywordArrayPlan implements SearchPlan {

    private static final int MIN_KEYWORD_LENGTH = 2;

    private final StoryIndexQuery index;

    public KeywordArrayPlan(StoryIndexQuery index) {
        this.index = index;
    }

    @Override
    public int number() {
        return 1;
    }

    @Override
    public String name() {
        return "키워드 완전 일치";
    }

    @Override
    public PlanRun execute(Query query, int limit) {
        List<String> keywords = keywordsIn(SearchContext.userQuestion(query), index.keywordVocabulary());
        String field = SearchContext.techField(query);
        String type = SearchContext.category(query);
        return new PlanRun(index.byKeywords(keywords, field, type, limit),
                PlanConditions.keywords(keywords, field, type));
    }

    /** 저장된 키워드 중 질문 안에 들어 있는 것. 목록 순서를 따른다. */
    static List<String> keywordsIn(String question, Collection<String> vocabulary) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return vocabulary.stream()
                .filter(k -> k != null && k.strip().length() >= MIN_KEYWORD_LENGTH)
                .filter(k -> containsWord(text, k.strip().toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private static boolean containsWord(String text, String keyword) {
        for (int at = text.indexOf(keyword); at >= 0; at = text.indexOf(keyword, at + 1)) {
            int end = at + keyword.length();
            boolean startsClean = at == 0 || !isAsciiAlnum(text.charAt(at - 1));
            boolean endsClean = end == text.length() || !isAsciiAlnum(text.charAt(end));
            if (startsClean && endsClean) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
