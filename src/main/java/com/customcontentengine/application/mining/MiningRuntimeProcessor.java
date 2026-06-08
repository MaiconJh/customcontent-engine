package com.customcontentengine.application.mining;

import com.customcontentengine.domain.mining.MiningSession;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.MiningCompletionPort;
import com.customcontentengine.port.MiningVisualPort;
import com.customcontentengine.port.SchedulerPort;
import java.util.List;
import java.util.Objects;

public final class MiningRuntimeProcessor {
    private final MiningSessionService sessionService;
    private final MiningVisualPort visualPort;
    private final MiningCompletionPort completionPort;
    private final SchedulerPort schedulerPort;

    public MiningRuntimeProcessor(
            MiningSessionService sessionService,
            MiningVisualPort visualPort,
            MiningCompletionPort completionPort,
            SchedulerPort schedulerPort) {
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
        this.visualPort = Objects.requireNonNull(visualPort, "visualPort");
        this.completionPort = Objects.requireNonNull(completionPort, "completionPort");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort");
    }

    public int processActiveSessions(long nowMillis, int maxSessions) {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("maxSessions must be positive");
        }

        List<MiningSession> activeSessions = sessionService.activeSessions();
        int scheduled = 0;
        for (MiningSession session : activeSessions) {
            if (scheduled >= maxSessions) {
                break;
            }
            schedulerPort.runOnRegion(session.target(), () -> processSession(session.actorKey(), session.target(), nowMillis));
            scheduled++;
        }
        return scheduled;
    }

    public MiningRuntimeUpdate processSession(String actorKey, WorldPosition target, long nowMillis) {
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(target, "target");

        var active = sessionService.getActiveSession(actorKey);
        if (active.isEmpty() || !active.get().target().equals(target)) {
            return MiningRuntimeUpdate.noSession();
        }
        MiningRuntimeUpdate update = sessionService.processSessionForRuntime(actorKey, nowMillis);
        if (update.completed()) {
            completionPort.complete(new MiningCompletionPort.CompletionRequest(
                    actorKey,
                    target,
                    active.get().toolId()));
        }
        applyVisual(actorKey, target, update);
        return update;
    }

    private void applyVisual(String actorKey, WorldPosition target, MiningRuntimeUpdate update) {
        switch (update.visualAction()) {
            case UPDATE_STAGE -> visualPort.updateMiningStage(actorKey, target, update.currentStage());
            case CLEAR_VISUAL -> visualPort.clearMiningVisual(actorKey);
            case NONE -> {
            }
        }
    }
}
