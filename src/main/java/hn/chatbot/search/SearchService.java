package hn.chatbot.search;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

/**
 * 4개 계획을 수행하고 storyId 기준으로 중복을 제거한 후보를 돌려준다(최대 20건).
 *
 * Spring AI 의 DocumentRetriever 다. 세션 5 에서는 RetrievalAugmentationAdvisor 가 질문마다
 * 부르고, 세션 6 에서는 searchIssues 도구가 부른다. 어느 쪽이든 입력은 Query 하나다.
 *
 * 돌려주는 Document 는 후보 하나다. Candidate.toDocument 로 만들고 Candidate.from 으로 되읽는다.
 * 순서는 계획 번호순으로 처음 찾아낸 순서다. 점수를 합쳐 다시 정렬하지 않는다.
 *
 * 계획별 요약(PlanSummary 목록)과 병합 건수는 SearchContext.record 로 query 의 컨텍스트에
 * 기록한다. 화면 사이드바가 이 값을 읽는다. 반환값인 Document 목록에는 담을 수 없기 때문이다.
 */
public interface SearchService extends DocumentRetriever {

    @Override
    List<Document> retrieve(Query query);
}
