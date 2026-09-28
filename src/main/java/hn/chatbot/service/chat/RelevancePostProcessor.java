package hn.chatbot.service.chat;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.List;

/**
 * 후보 중 질문에 실제로 답이 되는 것을 골라 근거로 만든다(최대 EVIDENCE_MAX 건).
 *
 * Spring AI 의 DocumentPostProcessor 다. RagChatServiceShell 에서는 RetrievalAugmentationAdvisor 가
 * SearchService 로 찾은 후보를 넘겨 부르고, AgentChatServiceShell 에서는 searchIssues 가
 * SearchService.retrieve 다음에 직접 부른다.
 *
 * 받는 Document 는 SearchService 가 돌려준 후보다. 돌려주는 Document 는 근거 하나다.
 * 후보의 metadata 에 근거 카드 필드를 더한 것이고, 판단 순서가 곧 근거 카드 순서다.
 *
 * 질문으로 쓰는 query 는 부르는 쪽마다 다르다. Advisor 는 사용자 원래 질문을 넘기고,
 * searchIssues 는 모델이 다듬은 검색어를 넘긴다.
 */
public interface RelevancePostProcessor extends DocumentPostProcessor {

    /** 돌려주는 근거의 최대 건수. */
    int EVIDENCE_MAX = 5;

    @Override
    List<Document> process(Query query, List<Document> documents);
}
