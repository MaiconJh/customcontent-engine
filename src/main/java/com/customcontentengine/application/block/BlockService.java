package com.customcontentengine.application.block;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
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

    public PlaceBlockResult handlePlace(CustomItemId itemId, WorldPosition position) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(position, "position");
        if (definitions.findItem(itemId).isEmpty()) {
            return PlaceBlockResult.unknownItem("Unknown custom item: " + itemId.value());
        }

        CustomBlockId blockId = new CustomBlockId(itemId.value());
        Optional<BlockDef> block = definitions.findBlock(blockId);
        if (block.isEmpty()) {
            return PlaceBlockResult.notCustomBlockItem("Custom item is not a custom block: " + itemId.value());
        }

        try {
            blockStore.put(position, block.get().numericId());
            return PlaceBlockResult.success();
        } catch (RuntimeException exception) {
            return PlaceBlockResult.storeFailed(
                    "Could not store custom block " + blockId.value() + ": " + exception.getMessage());
        }
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

    public enum PlaceBlockStatus {
        SUCCESS,
        UNKNOWN_ITEM,
        NOT_CUSTOM_BLOCK_ITEM,
        STORE_FAILED
    }

    public record PlaceBlockResult(PlaceBlockStatus status, String message) {
        public PlaceBlockResult {
            Objects.requireNonNull(status, "status");
            message = Objects.requireNonNull(message, "message");
        }

        public static PlaceBlockResult success() {
            return new PlaceBlockResult(PlaceBlockStatus.SUCCESS, "Custom block stored.");
        }

        public static PlaceBlockResult unknownItem(String message) {
            return new PlaceBlockResult(PlaceBlockStatus.UNKNOWN_ITEM, message);
        }

        public static PlaceBlockResult notCustomBlockItem(String message) {
            return new PlaceBlockResult(PlaceBlockStatus.NOT_CUSTOM_BLOCK_ITEM, message);
        }

        public static PlaceBlockResult storeFailed(String message) {
            return new PlaceBlockResult(PlaceBlockStatus.STORE_FAILED, message);
        }
    }
}
