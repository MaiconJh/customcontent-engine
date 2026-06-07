package com.customcontentengine.application.mechanic;

import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import java.util.Objects;
import java.util.Optional;

public class AreaBreakEventTriggerService {
    private final MechanicBindingRegistry mechanicBindings;
    private final MechanicId mechanicId;
    private final AreaBreakRuntimeService runtimeService;

    public AreaBreakEventTriggerService(
            MechanicBindingRegistry mechanicBindings,
            MechanicId mechanicId,
            AreaBreakRuntimeService runtimeService) {
        this.mechanicBindings = Objects.requireNonNull(mechanicBindings, "mechanicBindings");
        this.mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
    }

    public Optional<MechanicResult> trigger(CustomItemId itemId, WorldPosition origin, String actorKey) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");
        if (!mechanicBindings.contains(itemId, MechanicTrigger.ON_BLOCK_BREAK, mechanicId)) {
            return Optional.empty();
        }
        return Optional.of(runtimeService.executeAdditionalArea(origin, actorKey));
    }
}
