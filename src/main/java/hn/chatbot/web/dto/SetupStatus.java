package hn.chatbot.web.dto;

import hn.chatbot.service.setup.model.SetupState;

import java.util.List;
import java.util.Map;

/**
 * 브라우저가 읽는 셋업 진행 상황. 컨트롤러가 SetupProgress 에서 옮긴다.
 *
 * 화면의 진행바가 다음 항등식에 의존한다.
 *   target = completed + skipped + inProgress + excluded + pending
 *
 * skipped 는 이미 처리해 이번 실행에서 건너뛴 스토리 수다.
 *
 * searchableStories 는 이 항등식에 포함되지 않는다. 저장소 전체의 수치다.
 */
public record SetupStatus(
        SetupState state,
        int target,
        int completed,
        int skipped,
        int inProgress,
        int excluded,
        int pending,
        int searchableStories,
        Integer etaSeconds,
        List<StageStatus> stages,
        Exclusions exclusions,
        List<LogEntry> recentLogs) {

    public record Exclusions(ExclusionGroup mechanical, ExclusionGroup judged) {
    }

    public record ExclusionGroup(int total, Map<String, Integer> reasons) {
    }
}
