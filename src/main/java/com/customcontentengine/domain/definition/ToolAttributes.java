package com.customcontentengine.domain.definition;

public record ToolAttributes(double damage, double speed, int durability) {
    public ToolAttributes {
        if (damage < 0.0) {
            throw new IllegalArgumentException("damage must not be negative");
        }
        if (speed < 0.0) {
            throw new IllegalArgumentException("speed must not be negative");
        }
        if (durability < 0) {
            throw new IllegalArgumentException("durability must not be negative");
        }
    }
}
