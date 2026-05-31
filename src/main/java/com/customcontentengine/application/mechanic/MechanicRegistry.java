package com.customcontentengine.application.mechanic;

import com.customcontentengine.internalapi.mechanic.Mechanic;
import java.util.Collection;
import java.util.List;

public final class MechanicRegistry {
    private final List<Mechanic> mechanics;

    public MechanicRegistry(Collection<Mechanic> mechanics) {
        this.mechanics = List.copyOf(mechanics == null ? List.of() : mechanics);
    }

    public List<Mechanic> mechanics() {
        return mechanics;
    }
}
