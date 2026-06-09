package com.customcontentengine.domain.durability;

public record ToolDurability(int max, int current) {
    public ToolDurability {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive but was " + max);
        }
        if (current < 0) {
            throw new IllegalArgumentException("current must not be negative but was " + current);
        }
        if (current > max) {
            throw new IllegalArgumentException("current cannot exceed max but was " + current + " with max " + max);
        }
    }

    public boolean isBroken() {
        return current <= 0;
    }
}