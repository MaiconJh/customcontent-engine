package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.WorldPosition;

@FunctionalInterface
public interface RegionSafetyPort {
    boolean canAccess(WorldPosition position);
}
