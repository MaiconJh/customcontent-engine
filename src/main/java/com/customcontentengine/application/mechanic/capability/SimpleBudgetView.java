package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import java.util.Objects;

public final class SimpleBudgetView implements BudgetView {
    private final int maxBudget;
    private int consumed;

    public SimpleBudgetView(int maxBudget) {
        this.maxBudget = maxBudget;
        this.consumed = 0;
    }

    @Override
    public boolean tryConsume(WorldPosition position) {
        Objects.requireNonNull(position, "position");
        if (consumed >= maxBudget) {
            return false;
        }
        consumed++;
        return true;
    }
}