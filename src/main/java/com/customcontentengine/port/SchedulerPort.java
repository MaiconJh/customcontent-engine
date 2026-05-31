package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface SchedulerPort {
    void runOnRegion(WorldPosition position, Runnable task);
}
