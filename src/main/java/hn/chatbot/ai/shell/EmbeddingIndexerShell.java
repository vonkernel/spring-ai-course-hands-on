package hn.chatbot.ai.shell;

import hn.chatbot.ai.EmbeddingIndexer;
import hn.chatbot.ai.IssueAnalysis;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

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
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }

    @Override
    public void indexBody(long storyId, IssueAnalysis analysis, List<String> chunks) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
