package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.BlockPlacement;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Objects;

public final class StoredBlockPlacement implements BlockPlacement {
    private final BlockStorePort blockStore;
    private final WorldMutationPort worldMutation;
    private final RegionSafetyPort regionSafety;

    public StoredBlockPlacement(BlockStorePort blockStore, WorldMutationPort worldMutation) {
        this(blockStore, worldMutation, position -> true);
    }

    public StoredBlockPlacement(BlockStorePort blockStore, WorldMutationPort worldMutation, RegionSafetyPort regionSafety) {
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
    }

    @Override
    public void placeBlock(WorldPosition position, short numericId) {
        if (!regionSafety.canAccess(position)) {
            return;
        }
        blockStore.put(position, numericId);
        worldMutation.setBlockMaterial(position, "NOTE_BLOCK");
    }

    @Override
    public void placeMaterial(WorldPosition position, String materialName) {
        if (!regionSafety.canAccess(position)) {
            return;
        }
        blockStore.remove(position);
        worldMutation.setBlockMaterial(position, materialName);
    }
}