package com.customcontentengine.adapter.platform;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.RegionSafetyPort;
import java.util.Objects;

public final class PaperRegionSafetyAdapter implements RegionSafetyPort {
    @Override
    public boolean canAccess(WorldPosition position) {
        Objects.requireNonNull(position, "position");
        return true;
    }
}
