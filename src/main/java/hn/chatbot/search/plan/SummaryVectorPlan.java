package hn.chatbot.search.plan;

import hn.chatbot.search.PlanConditions;
import hn.chatbot.search.PlanHit;
import hn.chatbot.search.PlanRun;
import hn.chatbot.search.SearchContext;
import hn.chatbot.search.SearchPlan;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 검색 계획 4 — 요약 벡터 검색.
 *
 * 요약은 스토리당 1행이라 접을 필요가 없다.
 *
 * Spring AI 의 VectorStoreDocumentRetriever 로 검색한다. 주입된 벡터 스토어와 topK ·
 * similarityThreshold 로 만들고, retrieve(Query) 에 검색어를 넘긴다.
 * 설정값: topK 는 limit, similarityThreshold 는 0.3.
 *
 * PlanHit 의 score 는 코사인 거리다(1 - Document.getScore()). 스토리 id 는 metadata 의
 * storyId 를 Number 로 받아 longValue() 로 읽는다. JSONB 에서 되읽으면 Integer 로 올 수 있다.
 *
 * 필터는 Query 의 컨텍스트에 VectorStoreDocumentRetriever.FILTER_EXPRESSION 키로 실어 넘긴다.
 * suitable 은 항상 걸고, techField · category 는 값이 있을 때만 건다.
 * 두 값은 SearchContext.techField · SearchContext.category 로 읽는다. 빈 문자열은 이미 null 로 바뀌어 있다.
 *
 * 결과와 함께 실행한 조건을 돌려준다. 조건 문자열은 PlanConditions.vector 로 만든다.
 */
@Component
public class SummaryVectorPlan implements SearchPlan {

    /** 코사인 유사도 하한. */
    private static final double SIMILARITY_THRESHOLD = 0.3;

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
        String field = SearchContext.techField(query);
        String type = SearchContext.category(query);
        String condition = PlanConditions.vector(query.text(), field, type, limit);

        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(limit)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        Map<String, Object> context = new HashMap<>(query.context());
        context.put(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filter(field, type));
        List<Document> documents = retriever.retrieve(query.mutate().context(context).build());

        List<PlanHit> hits = documents.stream()
                .map(document -> new PlanHit(
                        ((Number) document.getMetadata().get("storyId")).longValue(),
                        1.0 - document.getScore()))
                .toList();

        return new PlanRun(hits, condition);
    }

    private static Filter.Expression filter(String techField, String category) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op expression = b.eq("suitable", true);
        if (techField != null) {
            expression = b.and(expression, b.eq("techField", techField));
        }
        if (category != null) {
            expression = b.and(expression, b.eq("category", category));
        }
        return expression.build();
    }
}
