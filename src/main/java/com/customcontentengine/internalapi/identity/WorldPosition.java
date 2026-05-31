package com.customcontentengine.internalapi.identity;

import java.util.Objects;

public record WorldPosition(String worldName, int x, int y, int z) {
    public WorldPosition {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
    }
}
