package com.customcontentengine.application.mechanic;

import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Objects;
import java.util.Set;

public final class AreaBreakTriggerPolicy {
    public static final CustomItemId MVP1_AREA_BREAK_TOOL_ID = new CustomItemId("ruby_pickaxe");

    private final Set<CustomItemId> allowedItemIds;

    public AreaBreakTriggerPolicy(Set<CustomItemId> allowedItemIds) {
        this.allowedItemIds = Set.copyOf(Objects.requireNonNull(allowedItemIds, "allowedItemIds"));
    }

    public static AreaBreakTriggerPolicy mvp1Default() {
        return new AreaBreakTriggerPolicy(Set.of(MVP1_AREA_BREAK_TOOL_ID));
    }

    public boolean shouldTrigger(CustomItemId itemId) {
        return allowedItemIds.contains(Objects.requireNonNull(itemId, "itemId"));
    }
}
