package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import java.util.Objects;

public final class StaticExecutionOrigin implements ExecutionOrigin {
    private final WorldPosition origin;

    public StaticExecutionOrigin(WorldPosition origin) {
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    @Override
    public WorldPosition origin() {
        return origin;
    }
}
