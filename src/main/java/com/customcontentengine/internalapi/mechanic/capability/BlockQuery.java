package com.customcontentengine.internalapi.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Optional;

public interface BlockQuery {
    Optional<Short> findCustomBlockNumericId(WorldPosition position);
}
