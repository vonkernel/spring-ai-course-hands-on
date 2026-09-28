package hn.chatbot.web;

import hn.chatbot.service.setup.model.SetupProgress;
import hn.chatbot.web.dto.LogEntry;
import hn.chatbot.web.dto.SetupStatus;
import hn.chatbot.web.dto.StageStatus;

/** 서비스 모델 → 브라우저 계약. 컨트롤러와 SSE 어댑터가 함께 쓴다. */
final class SetupDtoMapper {

    private SetupDtoMapper() {
    }

    static SetupStatus toStatus(SetupProgress p) {
        return new SetupStatus(
                p.state(), p.target(), p.completed(), p.skipped(), p.inProgress(), p.excluded(), p.pending(),
                p.searchableStories(), p.etaSeconds(),
                p.stages().stream()
                        .map(s -> new StageStatus(s.step(), s.name(), s.count(), s.state(), s.progress()))
                        .toList(),
                new SetupStatus.Exclusions(
                        new SetupStatus.ExclusionGroup(p.exclusions().mechanical().total(),
                                p.exclusions().mechanical().reasons()),
                        new SetupStatus.ExclusionGroup(p.exclusions().judged().total(),
                                p.exclusions().judged().reasons())),
                p.recentLogs().stream()
                        .map(l -> new LogEntry(l.at(), l.kind(), l.storyId(), l.title(), l.meta(), l.state()))
                        .toList());
    }
}
