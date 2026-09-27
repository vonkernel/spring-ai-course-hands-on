package hn.chatbot.search.plan;

import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchPlan;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 검색 계획 4 — 요약 벡터 검색.
 *
 * 요약은 스토리당 1행이라 접을 필요가 없다.
 *
 * Spring AI 의 VectorStoreDocumentRetriever 로 검색한다. 주입된 벡터 스토어와 topK ·
 * similarityThreshold 로 만들고, retrieve(Query) 에 검색어를 넘긴다.
 *
 * 필터는 Query 의 컨텍스트에 VectorStoreDocumentRetriever.FILTER_EXPRESSION 키로 실어 넘긴다.
 * suitable 은 항상 걸고, techField · category 는 값이 있을 때만 건다.
 * 두 값은 SearchContext.techField · SearchContext.category 로 읽는다. 빈 문자열은 이미 null 로 바뀌어 있다.
 *
 * 결과와 함께 실행한 조건을 돌려준다. 조건 문자열은 PlanConditions.vector 로 만든다.
 *
 * 수강생이 채운다.
 */
@Component
public class SummaryVectorPlan implements SearchPlan {

    private final PgVectorStore vectorStore;

    public SummaryVectorPlan(@Qualifier("summaryVectorStore") PgVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public int number() {
        return 4;
    }

    @Override
    public String name() {
        return "요약 벡터 검색";
    }

    @Override
    public PlanRun execute(Query query, int limit) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
