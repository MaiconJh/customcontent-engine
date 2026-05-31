package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface ProtectionPort {
    boolean canBuild(String actorName, WorldPosition position);
}
