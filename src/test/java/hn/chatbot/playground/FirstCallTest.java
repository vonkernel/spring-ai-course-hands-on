package hn.chatbot.playground;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 1 — 연결 확인. 완성본이다. 채울 것이 없고 돌리기만 한다.
 *
 * 모델 호출 · 임베딩 · pgvector 세 가지가 한 번에 확인된다. 통과하면 환경이
 * 준비된 것이고, 실패하면 키나 Docker 문제이지 구현 문제가 아니다.
 *
 * 기본 ./gradlew test 에서는 빠진다. OPENAI_API_KEY 가 필요해서,
 * 함께 돌리면 키 없이 전부 통과한다는 기준이 흐려지기 때문이다.
 *
 *   ./gradlew playground --tests '*FirstCallTest'
 */
@Tag("playground")
@SpringBootTest
class FirstCallTest {

    @Autowired ChatClient.Builder builder;

    /** PgVectorStore 빈이 둘이라 타입만으로는 주입되지 않는다. */
    @Autowired @Qualifier("summaryVectorStore") VectorStore vectorStore;

    @Test
    @DisplayName("모델 · 임베딩 · 벡터 스토어가 모두 붙어 있다")
    void 연결을_확인한다() {
        String answer = builder.build().prompt()
                .user("Reply with exactly one word: pong")
                .call()
                .content();

        System.out.println("[1] 모델 응답: " + answer);
        assertThat(answer).isNotBlank();

        // PgVectorStore 는 id 를 UUID.fromString 으로 파싱한다.
        // 접두어를 붙이면 IllegalArgumentException: UUID string too large 가 난다.
        String id = UUID.randomUUID().toString();
        vectorStore.add(List.of(new Document(id,
                "pgvector is a PostgreSQL extension for vector similarity search.",
                Map.of("source", "playground"))));
        System.out.println("[2] 임베딩 후 적재: " + id);

        try {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query("How can I run similarity search inside Postgres?")
                    .topK(1)
                    .build());

            System.out.println("[3] 검색 결과: " + hits.get(0).getText());
            assertThat(hits).isNotEmpty();
        } finally {
            vectorStore.delete(List.of(id));
        }
    }
}
