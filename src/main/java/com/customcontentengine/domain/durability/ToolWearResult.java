package com.customcontentengine.domain.durability;

public record ToolWearResult(ToolDurability newDurability, boolean shouldBreak) {
    public ToolWearResult {
        if (newDurability == null) {
            throw new IllegalArgumentException("newDurability must not be null");
        }
    }
}