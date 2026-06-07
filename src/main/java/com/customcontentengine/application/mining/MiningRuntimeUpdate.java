package com.customcontentengine.application.mining;

import com.customcontentengine.domain.mining.MiningProgress;
import com.customcontentengine.domain.mining.MiningStage;
import java.util.Objects;

public record MiningRuntimeUpdate(
        MiningProgress progress,
        MiningStage currentStage,
        boolean visualStageChanged,
        boolean completed,
        VisualAction visualAction) {
    public MiningRuntimeUpdate {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(visualAction, "visualAction");
    }

    public enum VisualAction {
        UPDATE_STAGE,
        CLEAR_VISUAL,
        NONE
    }

    public static MiningRuntimeUpdate inProgress(
            MiningProgress progress, MiningStage currentStage, boolean visualStageChanged) {
        return new MiningRuntimeUpdate(progress, currentStage, visualStageChanged, false, visualStageChanged ?
                VisualAction.UPDATE_STAGE :
                VisualAction.NONE);
    }

    public static MiningRuntimeUpdate completed(
            MiningProgress progress, MiningStage currentStage, boolean visualStageChanged) {
        return new MiningRuntimeUpdate(progress, currentStage, visualStageChanged, true, VisualAction.CLEAR_VISUAL);
    }

    public static MiningRuntimeUpdate noSession() {
        return new MiningRuntimeUpdate(
                new MiningProgress(0.0D),
                new MiningStage(0),
                false,
                false,
                VisualAction.NONE);
    }
}
