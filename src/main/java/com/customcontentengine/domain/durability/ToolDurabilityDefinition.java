package com.customcontentengine.domain.durability;

import java.util.Objects;

public record ToolDurabilityDefinition(int max, int damageOnCustomBlockBreak, ToolBreakPolicy breakPolicy) {
    public ToolDurabilityDefinition {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive but was " + max);
        }
        if (damageOnCustomBlockBreak < 0) {
            throw new IllegalArgumentException("damage_on_custom_block_break must not be negative but was " + damageOnCustomBlockBreak);
        }
        breakPolicy = Objects.requireNonNull(breakPolicy, "breakPolicy");
    }

    public ToolDurability initialDurability() {
        return new ToolDurability(max, max);
    }

    public ToolWearResult applyWear(ToolDurability current) {
        Objects.requireNonNull(current, "current");
        if (damageOnCustomBlockBreak == 0) {
            return new ToolWearResult(current, false);
        }
        int newCurrent = Math.max(0, current.current() - damageOnCustomBlockBreak);
        boolean shouldBreak = newCurrent == 0 && breakPolicy == ToolBreakPolicy.BREAK;
        return new ToolWearResult(
                new ToolDurability(current.max(), newCurrent),
                shouldBreak);
    }
}