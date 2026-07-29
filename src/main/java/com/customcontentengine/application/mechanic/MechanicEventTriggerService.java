package com.customcontentengine.application.mechanic;

import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import java.util.Map;
import java.util.Objects;

public final class MechanicEventTriggerService {
    private final MechanicBindingRegistry mechanicBindings;
    private final MechanicRuntimeService runtimeService;

    public MechanicEventTriggerService(MechanicBindingRegistry mechanicBindings, MechanicRuntimeService runtimeService) {
        this.mechanicBindings = Objects.requireNonNull(mechanicBindings, "mechanicBindings");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
    }

    public MechanicResult trigger(CustomItemId itemId, WorldPosition origin, String actorKey,
                                  Map<String, Object> extraArguments) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");

        return mechanicBindings.bindings().stream()
                .filter(binding -> binding.itemId().equals(itemId)
                        && binding.trigger() == MechanicTrigger.ON_BLOCK_BREAK)
                .findFirst()
                .map(binding -> runtimeService.execute(binding, origin, actorKey, extraArguments))
                .orElse(new MechanicResult.Done(0));
    }
}