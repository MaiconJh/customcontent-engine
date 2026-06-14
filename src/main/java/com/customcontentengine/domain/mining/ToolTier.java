package com.customcontentengine.domain.mining;

public record ToolTier(int level) {
    public ToolTier {
        if (level <= 0) {
            throw new IllegalArgumentException("tool tier level must be positive");
        }
    }
}
