package com.customcontentengine.application.mechanic;

import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MechanicBindingValidator {
    private static final Map<MechanicTrigger, Set<Capability>> TRIGGER_CAPABILITIES = Map.of(
            MechanicTrigger.ON_BLOCK_BREAK,
            Set.of(
                    Capability.BLOCK_QUERY,
                    Capability.BLOCK_MUTATION,
                    Capability.BLOCK_PLACEMENT,
                    Capability.BUDGET_VIEW,
                    Capability.COOLDOWN_VIEW,
                    Capability.DROP_SINK,
                    Capability.EXECUTION_ORIGIN,
                    Capability.MECHANIC_CONFIG));

    private final MechanicRegistry mechanicRegistry;
    private final Set<MechanicId> allowedMechanicIds;

    public MechanicBindingValidator(MechanicRegistry mechanicRegistry) {
        this(mechanicRegistry, mechanicRegistry.mechanics().stream()
                .map(mechanic -> mechanic.descriptor().id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public MechanicBindingValidator(MechanicRegistry mechanicRegistry, Set<MechanicId> allowedMechanicIds) {
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "mechanicRegistry");
        this.allowedMechanicIds = Set.copyOf(Objects.requireNonNull(allowedMechanicIds, "allowedMechanicIds"));
    }

    public void validate(MechanicBindingRegistry bindingRegistry) {
        Objects.requireNonNull(bindingRegistry, "bindingRegistry");
        for (MechanicBinding binding : bindingRegistry.bindings()) {
            Mechanic mechanic = mechanicRegistry.find(binding.mechanicId()).orElseThrow(() -> new IllegalArgumentException(
                    "Mechanic binding for item " + binding.itemId()
                            + " and trigger " + binding.trigger().yamlKey()
                            + " references unknown mechanic: " + binding.mechanicId()));
            if (!allowedMechanicIds.contains(binding.mechanicId())) {
                throw new IllegalArgumentException(
                        "Mechanic binding for item " + binding.itemId()
                                + " and trigger " + binding.trigger().yamlKey()
                                + " references mechanic not allowed in this phase: " + binding.mechanicId());
            }
            Set<Capability> availableCapabilities = TRIGGER_CAPABILITIES.getOrDefault(binding.trigger(), Set.of());
            if (!availableCapabilities.containsAll(mechanic.descriptor().requiredCapabilities())) {
                throw new IllegalArgumentException(
                        "Mechanic binding for item " + binding.itemId()
                                + " and trigger " + binding.trigger().yamlKey()
                                + " requires unavailable capabilities for mechanic: " + binding.mechanicId());
            }
        }
    }
}
