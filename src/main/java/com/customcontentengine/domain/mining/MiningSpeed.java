package com.customcontentengine.domain.mining;

public record MiningSpeed(double value) {
    public MiningSpeed {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("mining speed must be finite");
        }
        if (value <= 0.0D) {
            throw new IllegalArgumentException("mining speed must be greater than zero");
        }
    }
}
