package com.customcontentengine.internalapi.mechanic;

import java.util.Objects;
import java.util.Set;

public record MechanicDescriptor(
        MechanicId id,
        Set<Capability> requiredCapabilities,
        boolean readOnly,
        Set<Capability> optionalCapabilities) {
    public MechanicDescriptor {
        Objects.requireNonNull(id, "id");
        requiredCapabilities = Set.copyOf(requiredCapabilities == null ? Set.of() : requiredCapabilities);
        optionalCapabilities = Set.copyOf(optionalCapabilities == null ? Set.of() : optionalCapabilities);
    }

    public MechanicDescriptor(MechanicId id, Set<Capability> requiredCapabilities, boolean readOnly) {
        this(id, requiredCapabilities, readOnly, Set.of());
    }
}
