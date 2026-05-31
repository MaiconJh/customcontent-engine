package com.customcontentengine.internalapi.mechanic;

import java.util.Set;

public record MechanicDescriptor(String key, Set<Capability> capabilities) {
    public MechanicDescriptor {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }
}
