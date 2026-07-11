package com.customcontentengine.domain.mechanic;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MechanicBindingRegistry {
    private final List<MechanicBinding> allBindings;
    private final Map<CustomItemId, Map<MechanicTrigger, List<MechanicBinding>>> bindings;

    public MechanicBindingRegistry(Collection<MechanicBinding> bindings) {
        this.allBindings = List.copyOf(bindings == null ? List.<MechanicBinding>of() : bindings);

        Map<CustomItemId, Map<MechanicTrigger, java.util.ArrayList<MechanicBinding>>> mutable = new LinkedHashMap<>();
        for (MechanicBinding binding : allBindings) {
            mutable
                    .computeIfAbsent(binding.itemId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(binding.trigger(), ignored -> new java.util.ArrayList<>())
                    .add(binding);
        }

        Map<CustomItemId, Map<MechanicTrigger, List<MechanicBinding>>> immutable = new LinkedHashMap<>();
        mutable.forEach((itemId, byTrigger) -> {
            Map<MechanicTrigger, List<MechanicBinding>> triggerMap = new LinkedHashMap<>();
            byTrigger.forEach((trigger, bindingList) -> triggerMap.put(trigger, List.copyOf(bindingList)));
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
        return bindings.getOrDefault(itemId, Map.of()).getOrDefault(trigger, List.of()).stream()
                .map(MechanicBinding::mechanicId)
                .toList();
    }

    public MechanicBinding bindingFor(CustomItemId itemId, MechanicTrigger trigger, MechanicId mechanicId) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(mechanicId, "mechanicId");
        return bindings.getOrDefault(itemId, Map.of())
                .getOrDefault(trigger, List.of()).stream()
                .filter(binding -> binding.mechanicId().equals(mechanicId))
                .findFirst()
                .orElse(null);
    }

    public boolean contains(CustomItemId itemId, MechanicTrigger trigger, MechanicId mechanicId) {
        Objects.requireNonNull(mechanicId, "mechanicId");
        return bindingFor(itemId, trigger, mechanicId) != null;
    }

    public List<MechanicBinding> bindings() {
        return allBindings;
    }

    public List<MechanicBinding> bindingsFor(CustomItemId itemId, MechanicTrigger trigger) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(trigger, "trigger");
        return allBindings.stream()
                .filter(binding -> binding.itemId().equals(itemId) && binding.trigger() == trigger)
                .toList();
    }
}
