package com.customcontentengine.domain.mechanic;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record MechanicBinding(
        CustomItemId itemId,
        MechanicTrigger trigger,
        MechanicId mechanicId,
        Map<String, Object> arguments) {
    public MechanicBinding {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(mechanicId, "mechanicId");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public MechanicBinding(CustomItemId itemId, MechanicTrigger trigger, MechanicId mechanicId) {
        this(itemId, trigger, mechanicId, Map.of());
    }

    public Map<String, Object> arguments() {
        return arguments;
    }
}
