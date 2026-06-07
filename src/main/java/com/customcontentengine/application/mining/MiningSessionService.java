package com.customcontentengine.application.mining;

import com.customcontentengine.domain.mining.MiningDurationPolicy;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningProgress;
import com.customcontentengine.domain.mining.MiningSession;
import com.customcontentengine.domain.mining.MiningSessionId;
import com.customcontentengine.domain.mining.MiningStage;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MiningSessionService {
    private final MiningSessionRepository repository;
    private final MiningDurationPolicy durationPolicy;

    public MiningSessionService(MiningSessionRepository repository, MiningDurationPolicy durationPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.durationPolicy = Objects.requireNonNull(durationPolicy, "durationPolicy");
    }

    public MiningSession startSession(
            String actorKey,
            WorldPosition target,
            CustomItemId toolId,
            MiningHardness hardness,
            MiningSpeed speed,
            long nowMillis) {
        return startSession(
                new MiningSessionId(UUID.randomUUID().toString()),
                actorKey,
                target,
                toolId,
                hardness,
                speed,
                nowMillis);
    }

    public MiningSession startSession(
            MiningSessionId sessionId,
            String actorKey,
            WorldPosition target,
            CustomItemId toolId,
            MiningHardness hardness,
            MiningSpeed speed,
            long nowMillis) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(hardness, "hardness");
        Objects.requireNonNull(speed, "speed");

        long expectedDurationMillis = durationPolicy.expectedDurationMillis(hardness, speed);
        MiningSession session = MiningSession.start(
                sessionId,
                actorKey,
                target,
                toolId,
                nowMillis,
                expectedDurationMillis);
        repository.save(session);
        return session;
    }

    public Optional<MiningSession> getActiveSession(String actorKey) {
        Objects.requireNonNull(actorKey, "actorKey");
        return repository.findByActorKey(actorKey);
    }

    public Optional<MiningSession> cancelSession(String actorKey) {
        Objects.requireNonNull(actorKey, "actorKey");
        return repository.remove(actorKey);
    }

    public boolean cancelSession(String actorKey, WorldPosition target) {
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(target, "target");
        Optional<MiningSession> active = repository.findByActorKey(actorKey);
        if (active.isEmpty() || !active.get().target().equals(target)) {
            return false;
        }
        repository.remove(actorKey);
        return true;
    }

    public boolean clearSession(String actorKey) {
        Objects.requireNonNull(actorKey, "actorKey");
        return repository.remove(actorKey).isPresent();
    }

    public void clearAllSessions() {
        repository.clear();
    }

    public ProcessResult processSession(String actorKey, long nowMillis) {
        Objects.requireNonNull(actorKey, "actorKey");
        Optional<MiningSession> active = repository.findByActorKey(actorKey);
        if (active.isEmpty()) {
            return ProcessResult.noSession();
        }

        MiningSession session = active.get();
        MiningProgress progress = session.progressAt(nowMillis);
        MiningStage currentStage = session.visualStageAt(nowMillis);
        boolean visualStageChanged = session.visualStageChangedAt(nowMillis);

        if (progress.complete()) {
            repository.remove(actorKey);
            return ProcessResult.completed(session, progress, currentStage, visualStageChanged);
        }

        if (visualStageChanged) {
            session = session.withLastVisualStage(currentStage);
            repository.save(session);
        }

        return ProcessResult.inProgress(session, progress, currentStage, visualStageChanged);
    }

    public MiningRuntimeUpdate processSessionForRuntime(String actorKey, long nowMillis) {
        Objects.requireNonNull(actorKey, "actorKey");
        ProcessResult result = processSession(actorKey, nowMillis);

        if (result.session().isEmpty()) {
            return MiningRuntimeUpdate.noSession();
        }

        if (result.completed()) {
            return MiningRuntimeUpdate.completed(result.progress(), result.visualStage(), result.visualStageChanged());
        }

        return MiningRuntimeUpdate.inProgress(result.progress(), result.visualStage(), result.visualStageChanged());
    }

    public static final class ProcessResult {
        private final Optional<MiningSession> session;
        private final MiningProgress progress;
        private final MiningStage visualStage;
        private final boolean visualStageChanged;
        private final boolean completed;

        private ProcessResult(
                Optional<MiningSession> session,
                MiningProgress progress,
                MiningStage visualStage,
                boolean visualStageChanged,
                boolean completed) {
            this.session = Objects.requireNonNull(session, "session");
            this.progress = Objects.requireNonNull(progress, "progress");
            this.visualStage = Objects.requireNonNull(visualStage, "visualStage");
            this.visualStageChanged = visualStageChanged;
            this.completed = completed;
        }

        public static ProcessResult noSession() {
            return new ProcessResult(Optional.empty(), new MiningProgress(0.0D), new MiningStage(0), false, false);
        }

        public static ProcessResult inProgress(
                MiningSession session,
                MiningProgress progress,
                MiningStage visualStage,
                boolean visualStageChanged) {
            return new ProcessResult(Optional.of(session), progress, visualStage, visualStageChanged, false);
        }

        public static ProcessResult completed(
                MiningSession session,
                MiningProgress progress,
                MiningStage visualStage,
                boolean visualStageChanged) {
            return new ProcessResult(Optional.of(session), progress, visualStage, visualStageChanged, true);
        }

        public Optional<MiningSession> session() {
            return session;
        }

        public MiningProgress progress() {
            return progress;
        }

        public MiningStage visualStage() {
            return visualStage;
        }

        public boolean visualStageChanged() {
            return visualStageChanged;
        }

        public boolean completed() {
            return completed;
        }
    }
}
