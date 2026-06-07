package com.customcontentengine.application.mechanic;

import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MechanicContextFactory {
    private static final Map<Capability, Class<?>> CAPABILITY_TYPES = capabilityTypes();

    private final Map<Class<?>, Object> availableCapabilities;

    public MechanicContextFactory() {
        this(Map.of());
    }

    public MechanicContextFactory(Map<Class<?>, ?> availableCapabilities) {
        Objects.requireNonNull(availableCapabilities, "availableCapabilities");
        Map<Class<?>, Object> copy = new LinkedHashMap<>();
        availableCapabilities.forEach((type, instance) -> {
            Objects.requireNonNull(type, "capability type");
            Objects.requireNonNull(instance, "capability instance");
            if (!type.isInstance(instance)) {
                throw new IllegalArgumentException("Capability instance does not implement " + type.getName());
            }
            copy.put(type, instance);
        });
        this.availableCapabilities = Map.copyOf(copy);
    }

    public MechanicContext createContext(MechanicDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        Map<Class<?>, Object> contextCapabilities = new LinkedHashMap<>();
        for (Capability capability : descriptor.requiredCapabilities()) {
            Class<?> capabilityType = CAPABILITY_TYPES.get(capability);
            if (capabilityType == null) {
                throw new IllegalArgumentException("Unknown capability: " + capability);
            }
            Object capabilityInstance = availableCapabilities.get(capabilityType);
            if (capabilityInstance == null) {
                throw new IllegalArgumentException("Capability is not available: " + capability);
            }
            contextCapabilities.put(capabilityType, capabilityInstance);
        }
        return new MapBackedMechanicContext(contextCapabilities);
    }

    public MechanicContext createEmptyContext() {
        return new MapBackedMechanicContext(Map.of());
    }

    private static Map<Capability, Class<?>> capabilityTypes() {
        Map<Capability, Class<?>> types = new EnumMap<>(Capability.class);
        types.put(Capability.BLOCK_QUERY, BlockQuery.class);
        types.put(Capability.BLOCK_MUTATION, BlockMutation.class);
        types.put(Capability.BUDGET_VIEW, BudgetView.class);
        types.put(Capability.COOLDOWN_VIEW, CooldownView.class);
        types.put(Capability.DROP_SINK, DropSink.class);
        types.put(Capability.EXECUTION_ORIGIN, ExecutionOrigin.class);
        return Map.copyOf(types);
    }

    private static final class MapBackedMechanicContext implements MechanicContext {
        private final Map<Class<?>, Object> capabilities;

        private MapBackedMechanicContext(Map<Class<?>, Object> capabilities) {
            this.capabilities = Map.copyOf(capabilities);
        }

        @Override
        public <T> T require(Class<T> capabilityType) {
            Objects.requireNonNull(capabilityType, "capabilityType");
            Object capability = capabilities.get(capabilityType);
            if (capability == null) {
                throw new IllegalArgumentException("Capability is not available: " + capabilityType.getName());
            }
            return capabilityType.cast(capability);
        }
    }
}
