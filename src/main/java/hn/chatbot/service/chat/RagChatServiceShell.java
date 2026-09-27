package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Q&A 1단계: 질문마다 검색하는 RAG.
 *
 * RetrievalAugmentationAdvisor 가 질문마다 SearchService 로 검색하고, RelevancePostProcessor 로
 * 근거를 고른 뒤, 근거를 질문에 붙여 모델에 넘긴다. 답변 규칙은 system 프롬프트로 넣는다.
 * 구성은 실습 프롬프트로 전달한다.
 *
 * 답변 규칙
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

    private static final String MESSAGE =
            "아직 구현되지 않았습니다. RagChatServiceShell 을 채우면 이 창에 답변이 흐릅니다.";

    @Override
    public Flux<ChatEvent> chat(String conversationId, String question) {
        // 빈 구현. Q&A 탭을 잠그지 않고 어떤 질문에도 안내 문구를 token 으로 나눠 흘린다.
        // 채팅 창에서 글자가 하나씩 채워지므로 스트리밍 경로 전체가 처음부터 살아 있음을 확인할 수 있다.
        Flux<ChatEvent> tokens = Flux.fromArray(MESSAGE.split(""))
                .delayElements(Duration.ofMillis(40))
                .map(ChatEvent.Token::new)
                .cast(ChatEvent.class);

        return tokens.concatWith(Flux.just(new ChatEvent.Completed("STOP")));
    }
}
