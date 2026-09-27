package hn.chatbot.ai;

import java.util.Optional;

/**
 * 파이프라인 5단계 — 제목을 앵커로 삼아 정제 텍스트에서 본문만 남긴다.
 * 메뉴·관련기사·푸터를 제외한다. 본문을 찾지 못하면 비어 있는 값을 돌려준다.
 *
 * 본문 문장은 입력 그대로 옮기고 문단 사이의 빈 줄을 유지한다. 7단계 청킹이 빈 줄을
 * 문단 경계로 쓰므로, 줄바꿈을 없애면 청크가 하드 리밋으로만 잘린다.
 */
public interface ArticleBodyExtractor {

    Optional<String> extract(String title, String cleanedText);
}
