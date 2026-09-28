package hn.chatbot.service.chat;

import hn.chatbot.ai.RelevanceJudge;
import hn.chatbot.service.chat.port.ChatQuery;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 후보를 RelevanceJudge 로 판단해 근거 Document 로 만든다.
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
 * RelevanceJudge 와 ChatQuery 가 주입돼 있다.
 */
@Component
public class RelevancePostProcessorShell implements RelevancePostProcessor {

    private final RelevanceJudge relevanceJudge;
    private final ChatQuery chatQuery;

    public RelevancePostProcessorShell(RelevanceJudge relevanceJudge, ChatQuery chatQuery) {
        this.relevanceJudge = relevanceJudge;
        this.chatQuery = chatQuery;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다. 이 메서드를 채우세요.");
    }
}
