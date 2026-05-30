package com.customcontentengine.application.budget;

import java.util.HashMap;
import java.util.Map;

public final class WorkBudgetManager {
    private final int maxOperationsPerRegion;
    private final Map<RegionBudgetKey, WorkBudget> budgets = new HashMap<>();

    public WorkBudgetManager(int maxOperationsPerRegion) {
        if (maxOperationsPerRegion < 0) {
            throw new IllegalArgumentException("maxOperationsPerRegion must not be negative");
        }
        this.maxOperationsPerRegion = maxOperationsPerRegion;
    }

    public WorkBudget budgetFor(RegionBudgetKey key) {
        return budgets.computeIfAbsent(key, ignored -> new WorkBudget(maxOperationsPerRegion));
    }

    public void reset() {
        budgets.clear();
    }
}
