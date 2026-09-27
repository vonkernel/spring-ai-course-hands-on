package hn.chatbot.service.chat;

import hn.chatbot.service.chat.model.ChatEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 빈 구현이다.
 *
 * Q&A 탭은 잠그지 않는다. 어떤 질문이 들어와도 안내 문구를 token 으로 나눠 흘린다.
 * 채팅 창에서 글자가 하나씩 채워지므로 스트리밍 경로 전체가 처음부터
 * 살아 있음을 확인할 수 있다.
 *
 * 수강생이 ChatService 를 구현한 뒤에는, 데이터가 없을 때 안내 문구를 같은 방식으로
 * 흘리는 것이 그 자리를 대신한다.
 *
 * 수강생이 두 단계에 걸쳐 채운다. 1단계는 RetrievalAugmentationAdvisor 로 질문마다 검색하는 RAG,
 * 2단계는 도구와 대화 기억을 쓰는 에이전트다. 단계별 구성은 실습 프롬프트로 전달한다.
 */
@Service
public class ChatServiceShell implements ChatService {

    private static final String MESSAGE =
            "아직 구현되지 않았습니다. ChatService 를 채우면 이 자리에 답변이 흐릅니다.";

    @Override
    public Flux<ChatEvent> chat(String conversationId, String question) {
        Flux<ChatEvent> tokens = Flux.fromArray(MESSAGE.split(""))
                .delayElements(Duration.ofMillis(40))
                .map(ChatEvent.Token::new)
                .cast(ChatEvent.class);

        return tokens.concatWith(Flux.just(new ChatEvent.Completed("STOP")));
    }
}
