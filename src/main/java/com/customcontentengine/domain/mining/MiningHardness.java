package com.customcontentengine.domain.mining;

public record MiningHardness(double value) {
    public MiningHardness {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("mining hardness must be finite");
        }
        if (value <= 0.0D) {
            throw new IllegalArgumentException("mining hardness must be greater than zero");
        }
    }
}
