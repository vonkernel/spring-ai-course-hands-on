package hn.chatbot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 벡터 스토어를 둘로 나눈다. 요약은 스토리당 1행, 청크는 N행이라 한 테이블에 섞으면
 * 짧은 요약과 긴 청크가 같은 순위 경쟁을 하고 "요약에서만 찾기" 라는 계획을 세울 수 없다.
 *
 * 반환 타입을 PgVectorStore 로 적는다. 공식 문서 예제는 VectorStore 인데,
 * 그대로 두 개를 선언하면 자동구성이 물러서지 않아 빈이 셋이 된다. 자동구성의
 * @ConditionalOnMissingBean 이 찾는 타입이 메서드 반환 타입인 PgVectorStore 라서,
 * 인터페이스로 선언하면 조건이 알아보지 못한다. 세 번째 빈은 존재하지 않는
 * vector_store 테이블을 바라보는데 부팅은 그대로 성공하고
 * 질의 시점까지 아무 표시가 없다.
 *
 * 빈이 둘이므로 타입만으로는 주입되지 않는다. 쓰는 쪽은 @Qualifier 가 필요하다.
 *
 * 스키마는 init/04-vector.sql 이 만든다. metadata 를 JSONB 로 두기 위해서다.
 */
@Configuration
public class VectorStoreConfig {

    private static final int DIMENSIONS = 1536;   // text-embedding-3-large 를 1536차원으로 받음

    @Bean
    public PgVectorStore summaryVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("summary_vector_store")
                .dimensions(DIMENSIONS)
                .initializeSchema(false)
                .build();
    }

    @Bean
    public PgVectorStore bodyVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("body_vector_store")
                .dimensions(DIMENSIONS)
                .initializeSchema(false)
                .build();
    }
}
