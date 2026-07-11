package com.customcontentengine.application.mechanic;

import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Trigger that dispatches {@code vein_miner} executions for the {@code ON_BLOCK_BREAK}
 * event when a binding exists for the acting custom item.
 *
 * <p>The {@link EnchantmentView} is supplied by the caller (the adapter layer),
 * keeping the application layer free of Bukkit dependencies.</p>
 */
public final class VeinMinerEventTriggerService {
    private final MechanicBindingRegistry mechanicBindings;
    private final MechanicId mechanicId;
    private final VeinMinerRuntimeService runtimeService;

    public VeinMinerEventTriggerService(
            MechanicBindingRegistry mechanicBindings,
            MechanicId mechanicId,
            VeinMinerRuntimeService runtimeService) {
        this.mechanicBindings = Objects.requireNonNull(mechanicBindings, "mechanicBindings");
        this.mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
    }

    public Optional<MechanicResult> trigger(
            CustomItemId itemId,
            WorldPosition origin,
            String actorKey,
            EnchantmentView enchantmentView) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");
        if (!mechanicBindings.contains(itemId, MechanicTrigger.ON_BLOCK_BREAK, mechanicId)) {
            return Optional.empty();
        }
        Map<String, Object> arguments = mechanicBindings.bindingsFor(itemId, MechanicTrigger.ON_BLOCK_BREAK).stream()
                .filter(binding -> binding.mechanicId().equals(mechanicId))
                .map(MechanicBinding::arguments)
                .findFirst()
                .orElse(Map.of());
        return Optional.of(runtimeService.execute(origin, actorKey, enchantmentView, arguments));
    }
}
