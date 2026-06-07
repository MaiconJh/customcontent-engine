package com.customcontentengine.port;

import com.customcontentengine.domain.mining.MiningStage;
import com.customcontentengine.internalapi.identity.WorldPosition;

public interface MiningVisualPort {
    void updateMiningStage(String actorKey, WorldPosition position, MiningStage stage);

    void clearMiningVisual(String actorKey);
}
