package com.customcontentengine.port;

import com.customcontentengine.domain.durability.ToolWearResult;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Optional;

public interface ToolWearPort {
    Optional<ToolWearResult> applyWearIfNeeded(String actorKey, CustomItemId toolId);
}