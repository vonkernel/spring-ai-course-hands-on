-- 스토어를 둘로 나눈다. 한 테이블에 섞으면 짧은 요약과 긴 청크가 같은 순위
-- 경쟁을 하게 되고 "요약에서만 찾기" 라는 검색 계획을 세울 수 없다.
--
-- metadata 를 Spring AI 기본값인 JSON 이 아니라 JSONB 로 둔다. Spring AI 는
-- 읽고 쓸 때마다 ::jsonb 로 캐스팅하므로, 처음부터 JSONB 면 그 캐스팅이
-- 무연산이 되고 jsonb_path_ops GIN 인덱스를 걸 수 있다.
--
-- 임베딩 모델은 text-embedding-3-large, 1536차원으로 받는다.

CREATE TABLE summary_vector_store (
    id        UUID  PRIMARY KEY,
    content   TEXT,                  -- analysis.summary
    metadata  JSONB,
    embedding VECTOR(1536)
);
CREATE INDEX ON summary_vector_store USING HNSW (embedding vector_cosine_ops);
CREATE INDEX ON summary_vector_store USING GIN (metadata jsonb_path_ops);

CREATE TABLE body_vector_store (
    id        UUID  PRIMARY KEY,
    content   TEXT,                  -- body_chunk.content
    metadata  JSONB,
    embedding VECTOR(1536)
);
CREATE INDEX ON body_vector_store USING HNSW (embedding vector_cosine_ops);
CREATE INDEX ON body_vector_store USING GIN (metadata jsonb_path_ops);
