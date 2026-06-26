package com.customcontentengine.internalapi.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Optional;

public interface BlockPlacement {
    void placeBlock(WorldPosition position, short numericId);
    void placeMaterial(WorldPosition position, String materialName);
}