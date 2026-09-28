package hn.chatbot.adapter.web;

import hn.chatbot.service.setup.model.FetchOutcome;
import hn.chatbot.service.setup.model.FetchedArticle;
import hn.chatbot.service.setup.port.ArticleSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * ArticleSource 포트의 HTTP 어댑터. 조회와 기계적 정제까지 맡는다.
 *
 * 정규식이 아니라 jsoup 으로 요소를 제거한다. 정규식으로
 * <form> 같은 태그를 지우면 닫는 태그를 잘못 잡아 본문까지 삼킨다.
 *
 * 기계적 정제로 줄일 수 있는 양은 여기까지다. 네비게이션 메뉴도 관련기사 목록도
 * 구조상 <div><a> 라 본문과 구분되지 않는다. 그 다음은 LLM 본문 추출이 맡는다.
 *
 * HTTP 상태 코드를 밖으로 내보내지 않는다. 서비스가 쓰는 것은 "받았나 · PDF 인가 ·
 * 실패했나" 셋뿐이고, 길이 판정은 수집처와 무관한 우리 정책이라 서비스가 한다.
 *
 * 이 클라이언트에만 연결 · 읽기 타임아웃을 둔다. 기본 설정에는 응답 타임아웃이 없어서
 * 연결만 붙잡고 응답하지 않는 사이트를 만나면 셋업이 그 스토리에서 끝없이 멈춘다.
 * 전역 설정(spring.http.client.*)으로 두지 않는 것은 같은 RestClient.Builder 를 쓰는
 * OpenAI 호출까지 잘리기 때문이다.
 */
@Component
public class HttpArticleSource implements ArticleSource {

    /** 본문을 담을 수 없는 요소와, 지워도 손실이 푸터·관련기사뿐인 요소. */
    private static final String DROP = String.join(",",
            "script", "style", "head", "svg", "template", "noscript",
            "iframe", "canvas", "audio", "video", "picture", "dialog", "object",
            "select", "option", "textarea", "form", "button", "label",
            "nav", "header", "footer", "aside");

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F'};
    private static final String BLOCKS = "p, div, section, article, li, tr, h1, h2, h3, h4, h5, h6, br";

    /** doc.text() 가 공백을 뭉개므로 블록 경계에 표식을 심어 두고 나중에 줄바꿈으로 바꾼다. */
    private static final String NEWLINE_MARK = "@@NL@@";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient client;

    public HttpArticleSource(RestClient.Builder builder,
                             @Value("${app.fetch.user-agent}") String userAgent) {
        this.client = builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT)))
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Override
    public FetchedArticle fetch(String url) {
        try {
            ResponseEntity<byte[]> response = client.get()
                    .uri(URI.create(url)).retrieve().toEntity(byte[].class);

            byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
            String contentType = response.getHeaders().getFirst("Content-Type");

            if (isPdf(body, contentType)) {
                return new FetchedArticle(url, FetchOutcome.PDF, "");
            }
            String text = strip(new String(body, StandardCharsets.UTF_8));
            return new FetchedArticle(url, FetchOutcome.OK, text);
        }
        catch (Exception e) {
            // 조회 실패는 파이프라인이 기록할 사실이다. 흐름을 끊지 않는다.
            return new FetchedArticle(url, FetchOutcome.FAILED, "");
        }
    }

    private static boolean isPdf(byte[] body, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("application/pdf")) {
            return true;
        }
        if (body.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (body[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /** 요소 제거 → 블록 경계 줄바꿈 → 엔티티 복원 → 공백 정규화. */
    static String strip(String html) {
        Document doc = Jsoup.parse(html);
        doc.select(DROP).remove();
        doc.outputSettings().prettyPrint(false);
        doc.select(BLOCKS).forEach(e -> e.after(NEWLINE_MARK));

        return doc.text()
                .replace(NEWLINE_MARK, "\n")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
