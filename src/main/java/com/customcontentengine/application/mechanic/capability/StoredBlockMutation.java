package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Objects;

public final class StoredBlockMutation implements BlockMutation {
    private final BlockStorePort blockStore;
    private final WorldMutationPort worldMutation;
    private final RegionSafetyPort regionSafety;

    public StoredBlockMutation(BlockStorePort blockStore, WorldMutationPort worldMutation) {
        this(blockStore, worldMutation, position -> true);
    }

    public StoredBlockMutation(
            BlockStorePort blockStore,
            WorldMutationPort worldMutation,
            RegionSafetyPort regionSafety) {
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
    }

    @Override
    public void breakBlock(WorldPosition position) {
        if (!regionSafety.canAccess(position)) {
            return;
        }
        blockStore.remove(position);
        worldMutation.setBlockMaterial(position, "AIR");
    }
}
