package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.port.BlockStorePort;
import java.util.Objects;
import java.util.Optional;

public final class StoredBlockQuery implements BlockQuery {
    private final BlockStorePort blockStore;

    public StoredBlockQuery(BlockStorePort blockStore) {
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
    }

    @Override
    public Optional<Short> findCustomBlockNumericId(WorldPosition position) {
        return blockStore.findNumericId(position);
    }
}
