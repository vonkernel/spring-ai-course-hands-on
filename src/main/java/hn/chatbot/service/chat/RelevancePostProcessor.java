package hn.chatbot.service.chat;

import hn.chatbot.ai.RelevanceJudge;
import hn.chatbot.service.chat.port.ChatQuery;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 후보 중 질문에 실제로 답이 되는 것을 골라 근거로 만든다. Spring AI 의 DocumentPostProcessor 다.
 *
 * RagChatServiceShell 에서는 RetrievalAugmentationAdvisor 가 SearchService 로 찾은 후보를 넘겨 부르고,
 * AgentChatServiceShell 에서는 searchIssues 가 SearchService.retrieve 다음에 직접 부른다.
 *
 * 흐름
 *
 * 1 후보가 없으면 판단을 건너뛰고 빈 목록을 돌려준다
 * 2 ChatQuery.bodyExcerpts 로 후보의 원문 앞부분을 가져온다. storyId 는 EvidenceDocuments.storyIds 로 뽑는다
 * 3 EvidenceDocuments.targets 로 판단 입력을 만들어 RelevanceJudge.selectRelevant 에 넘긴다.
 *   질문은 query.text(), 최대 EVIDENCE_MAX 건이다
 * 4 ChatQuery.storyDetails 로 고른 스토리의 상세를 가져온다
 * 5 EvidenceDocuments.evidence 로 근거 Document 를 만들어 돌려준다. 판단 순서가 곧 근거 카드 순서다
 *
 * 질문으로 쓰는 query 는 부르는 쪽마다 다르다. Advisor 는 사용자 원래 질문을 넘기고,
 * searchIssues 는 모델이 다듬은 검색어를 넘긴다.
 */
@Component
public class RelevancePostProcessor implements DocumentPostProcessor {

    static final int EVIDENCE_MAX = 5;

    private final RelevanceJudge relevanceJudge;
    private final ChatQuery chatQuery;

    public RelevancePostProcessor(RelevanceJudge relevanceJudge, ChatQuery chatQuery) {
        this.relevanceJudge = relevanceJudge;
        this.chatQuery = chatQuery;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
