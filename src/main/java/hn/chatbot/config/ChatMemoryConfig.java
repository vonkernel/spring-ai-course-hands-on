package hn.chatbot.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 대화 기억. 저장소는 JDBC 스타터가 자동구성한 JdbcChatMemoryRepository 이고,
 * 스키마는 init/05-chat-memory.sql 이 만든다.
 *
 * 창 크기를 기본값 20 에서 50 으로 늘린다. 검색 · 목록 도구가 찾은 결과를
 * 대화 기억에 직접 남기므로 한 턴에 쌓이는 메시지가 질문과 답변 두 개보다 많다.
 *
 * 이 빈을 선언하면 자동구성의 ChatMemory 는 물러선다. 쓰는 곳은 두 군데다.
 * AgentChatServiceShell 이 MessageChatMemoryAdvisor 에 넘기고, IssueTools 가 도구 결과를 add 한다.
 */
@Configuration
public class ChatMemoryConfig {

    private static final int MAX_MESSAGES = 50;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}
