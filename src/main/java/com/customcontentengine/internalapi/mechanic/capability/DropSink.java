package com.customcontentengine.internalapi.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface DropSink {
    void dropFor(WorldPosition position, short numericId);
}
