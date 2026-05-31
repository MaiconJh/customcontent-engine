package com.customcontentengine.application.block;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Objects;
import java.util.Optional;

public final class BlockService {
    private final DefinitionRegistry definitions;
    private final SchedulerPort scheduler;
    private final BlockStorePort blockStore;
    private final WorldMutationPort worldMutation;
    private final DropPort dropPort;

    public BlockService(
            DefinitionRegistry definitions,
            SchedulerPort scheduler,
            BlockStorePort blockStore,
            WorldMutationPort worldMutation,
            DropPort dropPort
    ) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
    }

    public Optional<BlockDef> findBlock(CustomBlockId id) {
        return definitions.findBlock(id);
    }

    public void place(WorldPosition position, CustomBlockId id) {
        BlockDef block = definitions.findBlock(id).orElseThrow(() -> new IllegalArgumentException("Unknown block id: " + id));
        scheduler.runOnRegion(position, () -> {
            worldMutation.setBlockMaterial(position, block.materialBase());
            blockStore.put(position, block.numericId());
        });
    }

    public void handleBreak(WorldPosition position) {
        scheduler.runOnRegion(position, () -> blockStore.findNumericId(position)
                .flatMap(definitions::findBlockByNumericId)
                .ifPresent(block -> {
                    dropPort.drop(position, block.drops());
                    blockStore.remove(position);
                }));
    }
}
