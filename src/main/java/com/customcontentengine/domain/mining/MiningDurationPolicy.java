package com.customcontentengine.domain.mining;

import java.util.Objects;

public record MiningDurationPolicy(long millisPerHardnessUnit) {
    public static final MiningDurationPolicy DEFAULT = new MiningDurationPolicy(1000L);

    public MiningDurationPolicy {
        if (millisPerHardnessUnit <= 0L) {
            throw new IllegalArgumentException("millisPerHardnessUnit must be positive");
        }
    }

    public long expectedDurationMillis(MiningHardness hardness, MiningSpeed speed) {
        Objects.requireNonNull(hardness, "hardness");
        Objects.requireNonNull(speed, "speed");
        double duration = (hardness.value() / speed.value()) * millisPerHardnessUnit;
        if (duration >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(duration));
    }
}
