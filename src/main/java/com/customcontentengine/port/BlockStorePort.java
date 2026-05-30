package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Optional;

public interface BlockStorePort {
    Optional<Short> findNumericId(WorldPosition position);

    void put(WorldPosition position, short numericId);

    void remove(WorldPosition position);
}
