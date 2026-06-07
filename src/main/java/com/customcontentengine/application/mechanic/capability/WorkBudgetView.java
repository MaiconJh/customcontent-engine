package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.application.budget.WorkBudget;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import java.util.Objects;

public final class WorkBudgetView implements BudgetView {
    private final WorkBudget budget;

    public WorkBudgetView(WorkBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    @Override
    public boolean tryConsume(WorldPosition position) {
        Objects.requireNonNull(position, "position");
        return budget.tryConsume(1);
    }
}
