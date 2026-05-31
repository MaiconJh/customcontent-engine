package com.customcontentengine.application.budget;

public record RegionBudgetKey(String worldName, int regionX, int regionZ) {
    public RegionBudgetKey {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
    }
}
