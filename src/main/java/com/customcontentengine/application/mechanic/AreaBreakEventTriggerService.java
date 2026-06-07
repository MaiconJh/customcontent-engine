package com.customcontentengine.application.mechanic;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import java.util.Objects;
import java.util.Optional;

public class AreaBreakEventTriggerService {
    private final AreaBreakTriggerPolicy triggerPolicy;
    private final AreaBreakRuntimeService runtimeService;

    public AreaBreakEventTriggerService(
            AreaBreakTriggerPolicy triggerPolicy,
            AreaBreakRuntimeService runtimeService) {
        this.triggerPolicy = Objects.requireNonNull(triggerPolicy, "triggerPolicy");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
    }

    public Optional<MechanicResult> trigger(CustomItemId itemId, WorldPosition origin, String actorKey) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");
        if (!triggerPolicy.shouldTrigger(itemId)) {
            return Optional.empty();
        }
        return Optional.of(runtimeService.executeAdditionalArea(origin, actorKey));
    }
}
