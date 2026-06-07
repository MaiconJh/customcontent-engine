package com.customcontentengine.application.mechanic;

import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MechanicRegistry {
    private final Map<MechanicId, Mechanic> mechanicsById = new LinkedHashMap<>();

    public MechanicRegistry() {
    }

    public MechanicRegistry(Collection<Mechanic> mechanics) {
        if (mechanics != null) {
            mechanics.forEach(this::register);
        }
    }

    public void register(Mechanic mechanic) {
        Objects.requireNonNull(mechanic, "mechanic");
        MechanicId id = mechanic.descriptor().id();
        if (mechanicsById.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate mechanic id: " + id);
        }
        mechanicsById.put(id, mechanic);
    }

    public Optional<Mechanic> find(MechanicId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(mechanicsById.get(id));
    }

    public List<Mechanic> mechanics() {
        return List.copyOf(mechanicsById.values());
    }
}
