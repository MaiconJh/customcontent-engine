package com.customcontentengine.domain.mining;

import java.util.Objects;

public record MiningStage(int value) {
    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 9;

    public MiningStage {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("mining stage must be between 0 and 9");
        }
    }

    public static MiningStage fromProgress(MiningProgress progress) {
        Objects.requireNonNull(progress, "progress");
        if (progress.complete()) {
            return new MiningStage(MAX_VALUE);
        }
        return new MiningStage((int) Math.floor(progress.value() * 10.0D));
    }
}
