package com.customcontentengine.domain.mechanic;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MechanicBindingRegistry {
    private final Map<CustomItemId, Map<MechanicTrigger, List<MechanicId>>> bindings;

    public MechanicBindingRegistry(Collection<MechanicBinding> bindings) {
        Map<CustomItemId, Map<MechanicTrigger, java.util.ArrayList<MechanicId>>> mutable = new LinkedHashMap<>();
        for (MechanicBinding binding : bindings == null ? List.<MechanicBinding>of() : bindings) {
            mutable
                    .computeIfAbsent(binding.itemId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(binding.trigger(), ignored -> new java.util.ArrayList<>())
                    .add(binding.mechanicId());
        }

        Map<CustomItemId, Map<MechanicTrigger, List<MechanicId>>> immutable = new LinkedHashMap<>();
        mutable.forEach((itemId, byTrigger) -> {
            Map<MechanicTrigger, List<MechanicId>> triggerMap = new LinkedHashMap<>();
            byTrigger.forEach((trigger, mechanicIds) -> triggerMap.put(trigger, List.copyOf(mechanicIds)));
            immutable.put(itemId, Map.copyOf(triggerMap));
        });
        this.bindings = Map.copyOf(immutable);
    }

    public static MechanicBindingRegistry empty() {
        return new MechanicBindingRegistry(List.of());
    }

    public List<MechanicId> mechanicIdsFor(CustomItemId itemId, MechanicTrigger trigger) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(trigger, "trigger");
        return bindings.getOrDefault(itemId, Map.of()).getOrDefault(trigger, List.of());
    }

    public boolean contains(CustomItemId itemId, MechanicTrigger trigger, MechanicId mechanicId) {
        Objects.requireNonNull(mechanicId, "mechanicId");
        return mechanicIdsFor(itemId, trigger).contains(mechanicId);
    }

    public List<MechanicBinding> bindings() {
        return bindings.entrySet().stream()
                .flatMap(itemEntry -> itemEntry.getValue().entrySet().stream()
                        .flatMap(triggerEntry -> triggerEntry.getValue().stream()
                                .map(mechanicId -> new MechanicBinding(
                                        itemEntry.getKey(),
                                        triggerEntry.getKey(),
                                        mechanicId))))
                .toList();
    }
}
