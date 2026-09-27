package hn.chatbot.service.setup;

import hn.chatbot.service.setup.model.LogKind;
import hn.chatbot.service.setup.model.LogRecord;
import hn.chatbot.service.setup.model.LogState;
import hn.chatbot.service.setup.model.SetupOutcome;
import hn.chatbot.service.setup.model.SetupProgress;
import hn.chatbot.service.setup.model.SetupRun;
import hn.chatbot.service.setup.model.SetupState;
import hn.chatbot.service.setup.model.StageProgress;
import hn.chatbot.service.setup.model.StageState;
import hn.chatbot.service.setup.port.SetupQuery;
import hn.chatbot.service.setup.port.StorySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 셋업 실행의 뼈대. 완성본이다.
 *
 * 인기 스토리 목록을 받아 스토리마다 StoryProcessor 를 호출하고, 결과를 진행 상황으로 옮긴다.
 * 실행 스레드, 중복 실행 방지, 중지, 진행 상황 계산, 로그, SSE 발행을 맡는다.
 * StoryProcessor 도 완성본이다. 수강생은 그것이 부르는 부품(ai/)을 채운다.
 *
 * 스토리는 app.setup.concurrency 건씩 동시에 처리한다. 올리면 OpenAI 와 HN API 가
 * 429 로 거절한다. StoryProcessor 구현은 스토리 하나에만
 * 관여하고 공유 상태를 두지 않으므로 병렬로 불러도 된다. 진행 상황 필드는 여러
 * 스레드가 함께 갱신하므로 전부 원자적 타입이다.
 *
 * 진행 상황은 target = completed + inProgress + excluded + pending 을 지킨다.
 * 화면의 진행바가 이 항등식에 의존한다.
 *
 * @Async 를 쓰지 않는다. 같은 빈 안에서 호출하면 프록시를 거치지 않아 동기로 실행되고,
 * 그러면 run 이 처리를 다 끝낸 뒤에야 응답해 409 판정도 무너진다.
 *
 * 부품이 아직 비어 있으면(UnsupportedOperationException) 진행 화면 시연으로 바꾼다.
 * 첫 예외가 나기 전에 함께 돌던 스토리는 실제로 수집되고, 4단계에서 제외된 것은 원본이 저장될 수 있다.
 * 분석 행은 생기지 않으므로 주제 탐색과 Q&A 탭은 잠긴 채다.
 */
@Service
public class PipelineSetupService implements SetupService {

    private static final Logger log = LoggerFactory.getLogger(PipelineSetupService.class);
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> STAGE_NAMES = List.of(
            "수집", "중복 확인", "댓글 검열", "기계적 정제",
            "LLM 본문 추출", "LLM 분석", "원문 청킹", "임베딩·저장");
    private static final int RECENT_LOGS = 8;
    /** SSE 발행 최소 간격. 병렬 처리에서 단계마다 발행하면 조회와 전송이 과해진다. */
    private static final long PUBLISH_INTERVAL_MILLIS = 200;

    private final StorySource storySource;
    private final StoryProcessor processor;
    private final SetupQuery setupQuery;
    private final SetupProgressPublisher publisher;
    /** 시연 모드에서 한 단계가 도는 시간. 8단계 전체가 약 20초다. */
    private final long demoStageMillis;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "setup-pipeline");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService pool;

    private volatile SetupState state = SetupState.IDLE;
    private volatile boolean stopRequested;
    private volatile int target;
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger excluded = new AtomicInteger();
    private final AtomicInteger inProgress = new AtomicInteger();
    private volatile Instant startedAt;
    private final AtomicLong lastPublishedAt = new AtomicLong();
    private final AtomicIntegerArray stageCounts = new AtomicIntegerArray(STAGE_NAMES.size());
    private final LinkedList<LogRecord> logs = new LinkedList<>();

    @Autowired
    public PipelineSetupService(StorySource storySource, StoryProcessor processor,
                                SetupQuery setupQuery, SetupProgressPublisher publisher,
                                @Value("${app.setup.concurrency}") int concurrency) {
        this(storySource, processor, setupQuery, publisher, 2_500, concurrency);
    }

    PipelineSetupService(StorySource storySource, StoryProcessor processor,
                         SetupQuery setupQuery, SetupProgressPublisher publisher,
                         long demoStageMillis, int concurrency) {
        this.storySource = storySource;
        this.processor = processor;
        this.setupQuery = setupQuery;
        this.publisher = publisher;
        this.demoStageMillis = demoStageMillis;
        this.pool = Executors.newFixedThreadPool(concurrency, new java.util.concurrent.ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "setup-story-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public synchronized SetupRun run(int limit) {
        stopRequested = false;
        state = SetupState.RUNNING;
        target = limit;
        completed.set(0);
        excluded.set(0);
        inProgress.set(0);
        startedAt = Instant.now();
        lastPublishedAt.set(0);
        for (int i = 0; i < STAGE_NAMES.size(); i++) {
            stageCounts.set(i, 0);
        }
        synchronized (logs) {
            logs.clear();
        }
        worker.execute(() -> pipeline(limit));
        return new SetupRun(UUID.randomUUID().toString(), SetupState.RUNNING, startedAt);
    }

    @Override
    public SetupProgress progress() {
        int t = target;
        int c = completed.get();
        int e = excluded.get();
        int p = inProgress.get();
        List<StageProgress> stages = new ArrayList<>();
        for (int i = 0; i < STAGE_NAMES.size(); i++) {
            int count = stageCounts.get(i);
            double ratio = t == 0 ? 0.0 : Math.min(1.0, (double) count / t);
            StageState s = state == SetupState.DONE ? StageState.DONE
                    : count == 0 ? StageState.PENDING
                    : ratio >= 1.0 ? StageState.DONE : StageState.RUNNING;
            stages.add(new StageProgress(i + 1, STAGE_NAMES.get(i), count, s, ratio));
        }
        List<LogRecord> recent;
        synchronized (logs) {
            recent = List.copyOf(logs.subList(0, Math.min(RECENT_LOGS, logs.size())));
        }
        return new SetupProgress(state, t, c, p, e, Math.max(0, t - c - e - p),
                setupQuery.countSearchableStories(), eta(t, c + e), stages, setupQuery.exclusions(), recent);
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    private void pipeline(int limit) {
        try {
            List<Long> ids = storySource.bestStoryIds(limit);
            target = ids.size();
            stageCounts.set(0, ids.size());
            publish(true);

            AtomicBoolean notImplemented = new AtomicBoolean();
            List<Future<?>> running = new ArrayList<>();
            for (long id : ids) {
                running.add(pool.submit(() -> processOne(id, notImplemented)));
            }
            for (Future<?> f : running) {
                try {
                    f.get();
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                catch (ExecutionException failed) {
                    log.error("스토리 처리 작업이 실패했다", failed.getCause());
                }
            }
            if (notImplemented.get()) {
                demo();
                return;
            }
            finish(stopRequested ? SetupState.STOPPED : SetupState.DONE);
        }
        catch (RuntimeException ex) {
            log.error("셋업 실패", ex);
            addLog(LogKind.EXCLUDE, 0L, "셋업", "오류: " + ex.getMessage(), LogState.ERROR);
            inProgress.set(0);
            finish(SetupState.FAILED);
        }
    }

    /** 스토리 하나를 처리한다. 설정한 개수만큼의 스레드가 동시에 이 메서드를 돈다. */
    private void processOne(long id, AtomicBoolean notImplemented) {
        if (stopRequested || notImplemented.get()) {
            return;
        }
        inProgress.incrementAndGet();
        publish();
        StoryOutcome outcome = null;
        try {
            outcome = processor.process(id, tracker());
        }
        catch (UnsupportedOperationException notYet) {
            notImplemented.set(true);
        }
        catch (RuntimeException ex) {
            log.warn("스토리 {} 처리 실패", id, ex);
            addLog(LogKind.EXCLUDE, id, String.valueOf(id), "오류: " + ex.getMessage(), LogState.ERROR);
            outcome = StoryOutcome.EXCLUDED;
        }
        finally {
            // 결과를 세기 전에 내린다. 반대로 하면 그 사이 한 건이 진행 중과 완료로 겹쳐 잡힌다.
            inProgress.decrementAndGet();
        }
        if (outcome == StoryOutcome.EXCLUDED) {
            excluded.incrementAndGet();
        }
        else if (outcome != null) {
            completed.incrementAndGet();
            if (outcome == StoryOutcome.SKIPPED) {
                addLog(LogKind.EXCLUDE, id, String.valueOf(id), "이미 처리됨", LogState.OK);
            }
        }
        publish();
    }

    /** 부품이 비어 있을 때 진행 화면만 보여 준다. DB 에는 쓰지 않는다. */
    private void demo() {
        addLog(LogKind.EXCLUDE, 0L, "빈 구현", "채우지 않은 부품이 있어 진행 화면만 보여 줍니다", LogState.WARN);
        int t = target;
        try {
            for (int step = 2; step <= STAGE_NAMES.size(); step++) {
                if (stopRequested) {
                    finish(SetupState.STOPPED);
                    return;
                }
                Thread.sleep(demoStageMillis);
                stageCounts.set(step - 1, t);
                excluded.set(step >= 6 ? Math.round(t * 0.21f) : step >= 4 ? Math.round(t * 0.15f) : 0);
                completed.set(Math.min(t * step / STAGE_NAMES.size(), t - excluded.get()));
                inProgress.set(step == STAGE_NAMES.size() ? 0
                        : Math.min(3, t - completed.get() - excluded.get()));
                addLog(LogKind.EXCLUDE, 0L, STAGE_NAMES.get(step - 1) + " 단계", "시연", LogState.OK);
                publish(true);
            }
            completed.set(t - excluded.get());
            inProgress.set(0);
            finish(SetupState.DONE);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private StageTracker tracker() {
        return new StageTracker() {
            @Override
            public void passed(int stage) {
                if (stage >= 2 && stage <= STAGE_NAMES.size()) {
                    stageCounts.incrementAndGet(stage - 1);
                    publish();
                }
            }

            @Override
            public void log(LogKind kind, long storyId, String title, String meta, LogState logState) {
                addLog(kind, storyId, title, meta, logState);
            }
        };
    }

    private void finish(SetupState finalState) {
        state = finalState;
        inProgress.set(0);
        publish(true);
        publisher.complete(new SetupOutcome(finalState, completed.get(), excluded.get(),
                setupQuery.countSearchableStories()));
    }

    private Integer eta(int t, int done) {
        if (state != SetupState.RUNNING || done == 0 || startedAt == null) {
            return null;
        }
        double perStoryMillis = (double) Duration.between(startedAt, Instant.now()).toMillis() / done;
        return (int) Math.ceil(perStoryMillis * Math.max(0, t - done) / 1000);
    }

    private void addLog(LogKind kind, long storyId, String title, String meta, LogState logState) {
        synchronized (logs) {
            logs.addFirst(new LogRecord(LocalTime.now().format(AT), kind, storyId, title, meta, logState));
            while (logs.size() > 50) {
                logs.removeLast();
            }
        }
    }

    private void publish() {
        publish(false);
    }

    /**
     * 진행 상황을 발행한다. force 가 아니면 PUBLISH_INTERVAL_MILLIS 안의 호출은 건너뛴다.
     * 병렬 처리에서 단계마다 발행하면 progress() 의 DB 조회와 SSE 전송이 과해진다.
     */
    private void publish(boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            long previous = lastPublishedAt.get();
            if (now - previous < PUBLISH_INTERVAL_MILLIS || !lastPublishedAt.compareAndSet(previous, now)) {
                return;
            }
        }
        else {
            lastPublishedAt.set(now);
        }
        try {
            publisher.publish(progress());
        }
        catch (RuntimeException e) {
            log.debug("진행 상황 발행 실패", e);
        }
    }
}
