package hn.chatbot.ai.shell;

import hn.chatbot.ai.EmbeddingIndexer;
import hn.chatbot.ai.IssueAnalysis;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 8단계 — 임베딩해 두 벡터 스토어에 적재한다.
 *
 * @Qualifier 가 필요하다. PgVectorStore 빈이 둘이라 타입만으로는
 * 주입되지 않는다. 요약은 스토리당 1행, 청크는 N행이라 스토어를 나눠 두었다.
 *
 * 메타데이터 키는 storyId · suitable · techField · category 이고, 두 스토어에 모두 넣는다.
 * chunkSeq 는 본문 스토어에만 넣는다. storyId 로 검색 결과를 스토리 단위로 되묶고,
 * 나머지는 필터 조건으로 쓴다.
 */
@Component
public class EmbeddingIndexerShell implements EmbeddingIndexer {

    private final PgVectorStore summaryVectorStore;
    private final PgVectorStore bodyVectorStore;

    public EmbeddingIndexerShell(@Qualifier("summaryVectorStore") PgVectorStore summaryVectorStore,
                                 @Qualifier("bodyVectorStore") PgVectorStore bodyVectorStore) {
        this.summaryVectorStore = summaryVectorStore;
        this.bodyVectorStore = bodyVectorStore;
    }

    @Override
    public void indexSummary(long storyId, IssueAnalysis analysis) {
        // 재실행 시 같은 내용이 중복 적재되지 않도록 먼저 지운다.
        summaryVectorStore.delete(storyIdFilter(storyId));

        Document document = new Document(
                UUID.randomUUID().toString(), analysis.summary(), commonMetadata(storyId, analysis));
        summaryVectorStore.add(List.of(document));
    }

    @Override
    public void indexBody(long storyId, IssueAnalysis analysis, List<String> chunks) {
        bodyVectorStore.delete(storyIdFilter(storyId));

        List<Document> documents = IntStream.range(0, chunks.size())
                .mapToObj(chunkSeq -> {
                    Map<String, Object> metadata = commonMetadata(storyId, analysis);
                    metadata.put("chunkSeq", chunkSeq);
                    return new Document(
                            UUID.randomUUID().toString(), chunks.get(chunkSeq), metadata);
                })
                .toList();
        bodyVectorStore.add(documents);
    }

    private static Filter.Expression storyIdFilter(long storyId) {
        return new FilterExpressionBuilder().eq("storyId", storyId).build();
    }

    private static Map<String, Object> commonMetadata(long storyId, IssueAnalysis analysis) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("storyId", storyId);
        metadata.put("suitable", analysis.suitable());
        metadata.put("techField", analysis.techField());
        metadata.put("category", analysis.category());
        return metadata;
    }
}
