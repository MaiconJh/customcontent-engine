package com.customcontentengine.domain.mining;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Objects;

public record MiningSession(
        MiningSessionId id,
        String actorKey,
        WorldPosition target,
        CustomItemId toolId,
        long startedAtMillis,
        long expectedDurationMillis,
        MiningStage lastVisualStage
) {
    public MiningSession {
        Objects.requireNonNull(id, "id");
        if (actorKey == null || actorKey.isBlank()) {
            throw new IllegalArgumentException("actorKey must not be blank");
        }
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(toolId, "toolId");
        if (expectedDurationMillis <= 0L) {
            throw new IllegalArgumentException("expectedDurationMillis must be positive");
        }
        lastVisualStage = Objects.requireNonNull(lastVisualStage, "lastVisualStage");
    }

    public static MiningSession start(
            MiningSessionId id,
            String actorKey,
            WorldPosition target,
            CustomItemId toolId,
            long startedAtMillis,
            long expectedDurationMillis) {
        return new MiningSession(
                id,
                actorKey,
                target,
                toolId,
                startedAtMillis,
                expectedDurationMillis,
                new MiningStage(0));
    }

    public MiningProgress progressAt(long nowMillis) {
        return MiningProgress.at(startedAtMillis, nowMillis, expectedDurationMillis);
    }

    public MiningStage visualStageAt(long nowMillis) {
        return MiningStage.fromProgress(progressAt(nowMillis));
    }

    public boolean visualStageChangedAt(long nowMillis) {
        return !lastVisualStage.equals(visualStageAt(nowMillis));
    }

    public MiningSession withLastVisualStage(MiningStage stage) {
        return new MiningSession(
                id,
                actorKey,
                target,
                toolId,
                startedAtMillis,
                expectedDurationMillis,
                stage);
    }
}
