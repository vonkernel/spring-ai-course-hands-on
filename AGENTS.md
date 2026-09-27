# AGENTS.md

Spring AI 실습 프로젝트. Hacker News 의 기술 이슈를 수집해 AI 로 분석·구조화하고,
그 결과로 의미 기반 검색과 근거 기반 Q&A 를 한다.

**이 저장소는 실습용이다.** 애플리케이션은 이미 기동되고 화면도 동작하지만, AI 호출 코드가
비어 있다. 이 빈 구현을 채우는 것이 실습이다.

## 빈 구현 위치

```
ai/shell/                 LLM · Moderation 호출 9개
search/shell/             검색 서비스
search/plan/              벡터 검색 계획 2개 (BodyVectorPlan · SummaryVectorPlan)
service/chat/RelevancePostProcessor   근거 후처리
service/chat/IssueTools   모델이 호출하는 도구 7종
service/chat/ChatServiceShell   Q&A 연결 (1단계 · 2단계)
```

**각 클래스의 Javadoc 이 구현 명세다.** 입력과 반환값, 주의 사항이
Javadoc 에 적혀 있다. 구현 전에 읽는다.

## 빈 구현 클래스 작업 절차

특정 클래스(또는 인터페이스)의 구현 요청을 받으면, 코드를 작성하기 전에 항상 아래 순서를 따른다.

1. **`docs/03-구현.md`** 에서 해당 클래스의 항목(역할·입력·출력·확인 방법·주의 사항)을 찾아 확인한다.
2. 해당 클래스와 그 클래스가 구현하는 **인터페이스의 Javadoc** 을 읽고
   명세(반환 계약, 예외 조건, 주의 사항)를 구현에 반영한다.
3. 구현을 완료한다.
4. 구현 완료 후 **`docs/04-코드-리뷰.md`** 의 다섯 가지 기준(완성본 수정 없음 · 계층 규칙 준수 ·
   계약대로 반환 · 예외 무시 없음 · `./gradlew test` 통과)에 따라 검증한다.

이 절차를 이후의 모든 구현 요청에 적용한다.

## 프롬프트와 생성 결과의 언어

실습에서 쓰는 프롬프트와 모델이 만드는 결과의 언어를 맞춘다. 수강생마다 언어가 갈리지 않게 한다.

- **빈 구현에 쓰는 프롬프트(system · user 문구)는 한국어로 쓴다.**
- **모델이 만드는 서술형 결과가 한국어로 나오도록 프롬프트에 명시한다.** 적지 않으면 영어 원문을
  따라가 결과마다 언어가 섞인다. 대상은 다음과 같다.
  - `IssueAnalyzer`: `summary` · `communityReaction` · `practicalImplication` · `unsuitableReason`
  - `TopicSummarizer`: 분야 요약
- **다음은 바꾸지 않는다.** 바꾸면 기능이 깨진다.
  - 원문을 그대로 옮기는 값: 본문 추출 결과(`ArticleBodyExtractor`), 마스킹 구간(`HarmfulPhraseDetector`),
    근거 대목(`RelevanceJudge` 의 `passage`). 원문이 영어면 영어 그대로 둔다
  - 허용값 목록의 값: `techField` · `category`
  - `keywords` 의 고유명사 · 제품명 표기(`VMware`, `llama.cpp`). 검색 계획 1 이 사용자가 쓴 표기로 찾는다
- Q&A 답변은 답변 규칙(`AnswerGenerator`)대로 질문과 같은 언어로 한다.

## solution 브랜치 열람 금지

이 저장소에는 모범답안을 담은 `solution` 브랜치가 있다. **빈 구현을 채울 때 그 브랜치를
열어 보지 않는다.** 체크아웃도, `git show solution:<경로>` 도, `git diff main solution` 도
하지 않는다. 베껴 쓰면 실습이 성립하지 않는다.

막히면 볼 곳은 둘이다. 해당 클래스와 그 클래스가 구현하는 **인터페이스의 Javadoc**,
그리고 **`docs/03-구현.md`** 의 해당 항목이다. 그 둘에 없는 것은 설계 판단에 맡긴 부분이다.

`solution` 은 수강생이 실습을 마친 뒤 자기 구현과 비교해 보는 용도다.

## 수정 금지 대상

아래는 완성본으로 제공된다. 수정하지 않는다.

```
domain/  persistence/  web/  adapter/  config/
service/*/model/  service/*/port/
service/topic/DefaultTopicService   service/setup/AnalysisMapper
service/chat/ChatTurn   service/chat/SearchEvidence   service/chat/SearchRecord   service/chat/EvidenceDocuments
service/setup/PipelineSetupService   service/setup/StageTracker   service/setup/PipelinePolicy
service/setup/StoryExtractor   service/setup/StoryTransformer   service/setup/StoryLoader
service/setup/DefaultStoryProcessor
ai/*.java (shell/ 제외)   search/*.java (shell/ 제외)
search/plan/KeywordArrayPlan   search/plan/FullTextPlan
init/*.sql  frontend/  src/main/resources/static/
src/test/ (단, src/test/java/hn/chatbot/playground/ 는 예외)
```

고쳐야 할 이유를 찾았다면 고치기 전에 사용자에게 알린다. 대개는 빈 구현 쪽에서
풀 수 있는 문제다.

## 확인

```bash
./gradlew test       # 계층 규칙과 계약 검증
./gradlew bootRun    # localhost:8080
./gradlew playground --tests '*<클래스>CheckTest'  # 빈 구현 하나만 확인. .env 의 OPENAI_API_KEY 필요
```

애플리케이션은 API 키를 저장소 루트의 `.env` 에서 읽는다. `.env` 의 내용을 출력하거나 커밋하지 않는다.

계층 규칙은 `LayeringTest` 가 강제한다. 규칙을 위반하면 테스트가 실패한다.

`./gradlew test` 는 키 없이 전부 통과해야 한다. 빈 구현은 `playground` 태그가 붙은
확인 테스트로 확인한다. 구현을 채우기 전까지 실패하는 것이 정상이다.
