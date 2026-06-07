package com.customcontentengine.internalapi.mechanic;

public interface MechanicContext {
    <T> T require(Class<T> capabilityType);
}
