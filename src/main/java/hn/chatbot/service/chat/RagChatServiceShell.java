package hn.chatbot.service.chat;

import hn.chatbot.search.SearchService;
import hn.chatbot.service.chat.model.ChatEvent;
import hn.chatbot.service.topic.TopicService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Q&A 1단계: 질문마다 검색하는 RAG.
 *
 * RetrievalAugmentationAdvisor 가 질문마다 SearchService 로 검색하고, RelevancePostProcessor 로
 * 근거를 고른 뒤, 근거를 질문에 붙여 모델에 넘긴다. 답변 지시는 한국어 system 프롬프트 상수로 둔다.
 *
 * 흐름
 *
 * 1 RetrievalAugmentationAdvisor 를 만든다. documentRetriever 는 SearchService,
 *   documentPostProcessors 는 RelevancePostProcessor, queryAugmenter 는 ContextualQueryAugmenter 이고
 *   그 documentFormatter 는 SearchEvidence::format 이다
 * 2 system 은 답변 지시 상수, user 는 질문으로 하고 이 Advisor 를 붙인다
 * 3 stream().chatClientResponse() 를 ChatTurn.streamWithEvidence 에 넘겨 돌려준다
 *
 * 답변 지시
 *
 * - 주어진 근거만으로 답한다. 근거에 없는 내용은 모른다고 한다
 * - 원문 타입(category)에 따라 서술 강도를 달리한다. 공식 발표와 개인 의견을 구분한다
 * - 커뮤니티 반응과 실무 시사점을 답에 넣는다
 * - 어떤 내용이 몇 번 근거에서 나왔는지 답에 표시한다. 사용자가 출처를 대조할 수 있어야 한다.
 *   그 번호가 SearchEvidence.Item.rank 이고 화면의 근거 카드 순서와 같다
 * - 질문과 같은 언어로 답한다
 *
 * 적재된 데이터가 없으면 모델을 부르지 않고 안내 문구를 token 으로 나눠 흘린다.
 */
@Service
public class RagChatServiceShell implements ChatService {

    private static final String NO_DATA_MESSAGE =
            "아직 적재된 이슈가 없습니다. 데이터 셋업 탭에서 먼저 셋업을 실행해 주세요.";

    private static final String ANSWER_INSTRUCTIONS = """
            너는 Hacker News 기술 이슈를 바탕으로 질문에 답하는 어시스턴트다.

            - 주어진 근거(evidence 목록)에 있는 내용만으로 답한다. 근거에 없는 내용은
              지어내지 말고 모른다고 말한다.
            - 원문 타입(category)에 따라 서술 강도를 달리한다. 공식 발표(OFFICIAL_ANNOUNCEMENT,
              RELEASE_NOTES)는 사실로 전달하고, 개인 의견(OPINION_ESSAY)이나 커뮤니티 질문
              (ASK_TELL_HN) 은 한 사람의 시각임을 분명히 한다.
            - 근거에 커뮤니티 반응(communityReaction)이나 실무 시사점(practicalImplication)이
              있으면 답에 포함한다.
            - 어떤 내용이 몇 번 근거에서 나왔는지 [1] 처럼 답에 표시한다. 사용자가 출처를 대조할
              수 있어야 한다. 그 번호는 근거의 rank 이고 화면의 근거 카드 번호와 같다.
            - 질문과 같은 언어로 답한다.
            """;

    private final ChatClient chatClient;
    private final TopicService topicService;
    private final Advisor retrievalAugmentation;

    public RagChatServiceShell(ChatClient.Builder builder, SearchService searchService,
                               RelevancePostProcessor relevancePostProcessor, TopicService topicService) {
        this.chatClient = builder.build();
        this.topicService = topicService;
        this.retrievalAugmentation = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(searchService)
                .documentPostProcessors(relevancePostProcessor)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .documentFormatter(SearchEvidence::format)
                        .build())
                .build();
    }

    @Override
    public Flux<ChatEvent> chat(String conversationId, String question) {
        if (topicService.count(null) == 0) {
            return guidance(NO_DATA_MESSAGE);
        }

        return new ChatTurn().streamWithEvidence(chatClient.prompt()
                .system(ANSWER_INSTRUCTIONS)
                .user(question)
                .advisors(retrievalAugmentation)
                .stream()
                .chatClientResponse());
    }

    private static Flux<ChatEvent> guidance(String message) {
        Flux<ChatEvent> tokens = Flux.fromArray(message.split(""))
                .delayElements(Duration.ofMillis(40))
                .map(ChatEvent.Token::new)
                .cast(ChatEvent.class);

        return tokens.concatWith(Flux.just(new ChatEvent.Completed("STOP")));
    }
}
