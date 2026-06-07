package com.customcontentengine.domain.mechanic;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.util.Objects;

public record MechanicBinding(CustomItemId itemId, MechanicTrigger trigger, MechanicId mechanicId) {
    public MechanicBinding {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(mechanicId, "mechanicId");
    }
}
