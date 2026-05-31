package com.customcontentengine.port;

import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.internalapi.identity.WorldPosition;

public interface DropPort {
    void drop(WorldPosition position, DropTable drops);
}
