package com.customcontentengine.internalapi.mechanic;

public interface Mechanic {
    MechanicDescriptor descriptor();

    MechanicResult execute(MechanicContext context);
}
