package com.customcontentengine.internalapi.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface BlockMutation {
    void breakBlock(WorldPosition position);
}
