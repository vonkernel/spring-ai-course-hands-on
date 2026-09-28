package hn.chatbot.service.setup.model;

import java.util.List;

/**
 * 셋업 진행 상황. 서비스 계층이 소유하고, 컨트롤러가 web/dto 로 옮긴다.
 *
 * 화면의 진행바가 다음 항등식에 의존한다.
 *   target = completed + skipped + inProgress + excluded + pending
 *
 * skipped 는 이미 처리해 이번 실행에서 건너뛴 스토리 수다.
 *
 * searchableStories 는 이 항등식에 포함되지 않는다.
 * 이번 실행의 진행 상황이 아니라 저장소 전체의 수치다.
 *
 * etaSeconds 는 산출할 수 없으면 null 이다.
 */
public record SetupProgress(
        SetupState state,
        int target,
        int completed,
        int skipped,
        int inProgress,
        int excluded,
        int pending,
        int searchableStories,
        Integer etaSeconds,
        List<StageProgress> stages,
        Exclusions exclusions,
        List<LogRecord> recentLogs) {
}
