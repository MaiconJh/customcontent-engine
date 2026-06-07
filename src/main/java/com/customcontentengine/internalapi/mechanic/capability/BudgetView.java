package com.customcontentengine.internalapi.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface BudgetView {
    boolean tryConsume(WorldPosition position);
}
