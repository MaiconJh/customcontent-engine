package com.customcontentengine.domain.mining;

public record MiningProgress(double value) {
    public MiningProgress {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("mining progress must be finite");
        }
        value = clamp(value);
    }

    public static MiningProgress at(long startedAtMillis, long nowMillis, long expectedDurationMillis) {
        if (expectedDurationMillis <= 0L) {
            throw new IllegalArgumentException("expectedDurationMillis must be positive");
        }
        long elapsedMillis = Math.max(0L, nowMillis - startedAtMillis);
        return new MiningProgress((double) elapsedMillis / (double) expectedDurationMillis);
    }

    public boolean complete() {
        return value >= 1.0D;
    }

    private static double clamp(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }
}
