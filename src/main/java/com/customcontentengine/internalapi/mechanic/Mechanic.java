package com.customcontentengine.internalapi.mechanic;

public interface Mechanic {
    MechanicDescriptor descriptor();

    default MechanicResult execute() {
        return MechanicResult.noop();
    }
}
