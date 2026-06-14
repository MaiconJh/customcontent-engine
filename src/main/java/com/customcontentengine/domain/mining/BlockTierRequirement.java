package com.customcontentengine.domain.mining;

public record BlockTierRequirement(int minimumLevel) {
    public BlockTierRequirement {
        if (minimumLevel <= 0) {
            throw new IllegalArgumentException("block tier requirement must be positive");
        }
    }
}
