package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Objects;

public final class StoredBlockMutation implements BlockMutation {
    private final BlockStorePort blockStore;
    private final WorldMutationPort worldMutation;

    public StoredBlockMutation(BlockStorePort blockStore, WorldMutationPort worldMutation) {
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
    }

    @Override
    public void breakBlock(WorldPosition position) {
        blockStore.remove(position);
        worldMutation.setBlockMaterial(position, "AIR");
    }
}
