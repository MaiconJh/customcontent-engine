package com.customcontentengine.internalapi.mechanic;

import java.util.Objects;
import java.util.Set;

public record MechanicDescriptor(MechanicId id, Set<Capability> requiredCapabilities, boolean readOnly) {
    public MechanicDescriptor {
        Objects.requireNonNull(id, "id");
        requiredCapabilities = Set.copyOf(requiredCapabilities == null ? Set.of() : requiredCapabilities);
    }
}
