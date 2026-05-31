package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface WorldMutationPort {
    void setBlockMaterial(WorldPosition position, String materialBase);
}
