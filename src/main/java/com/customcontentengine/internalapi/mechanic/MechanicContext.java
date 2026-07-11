package com.customcontentengine.internalapi.mechanic;

import java.util.Objects;
import java.util.Optional;

public interface MechanicContext {
    <T> T require(Class<T> capabilityType);

    default <T> Optional<T> optional(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType");
        try {
            return Optional.of(require(capabilityType));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
