package com.customcontentengine.application.block;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class BlockService {
    private static final Logger LOGGER = Logger.getLogger(BlockService.class.getName());
    private static final long ORPHAN_WARNING_INTERVAL_NANOS = 30_000_000_000L;

    private final DefinitionRegistry definitions;
    private final BlockStorePort blockStore;
    private final DropPort dropPort;
    private long lastOrphanWarningNanos = Long.MIN_VALUE;

    public BlockService(DefinitionRegistry definitions, BlockStorePort blockStore, DropPort dropPort) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
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

    public BreakBlockResult handleBreak(WorldPosition position) {
        Objects.requireNonNull(position, "position");
        Optional<Short> numericId;
        try {
            numericId = blockStore.findNumericId(position);
        } catch (RuntimeException exception) {
            return BreakBlockResult.storeFailed("Could not read custom block identity: " + exception.getMessage());
        }

        if (numericId.isEmpty()) {
            return BreakBlockResult.notCustomBlock();
        }

        Optional<BlockDef> block = definitions.findBlockByNumericId(numericId.get());
        if (block.isEmpty()) {
            try {
                blockStore.remove(position);
            } catch (RuntimeException exception) {
                return BreakBlockResult.storeFailed("Could not remove orphan custom block identity: " + exception.getMessage());
            }
            warnOrphan(position, numericId.get());
            return BreakBlockResult.orphanBlockRemoved();
        }

        try {
            blockStore.remove(position);
        } catch (RuntimeException exception) {
            return BreakBlockResult.storeFailed("Could not remove custom block identity: " + exception.getMessage());
        }

        try {
            dropPort.drop(position, block.get().drops());
        } catch (RuntimeException exception) {
            return BreakBlockResult.dropFailed("Could not drop custom block items: " + exception.getMessage());
        }

        return BreakBlockResult.customBlockBroken();
    }

    private void warnOrphan(WorldPosition position, short numericId) {
        long now = System.nanoTime();
        if (lastOrphanWarningNanos == Long.MIN_VALUE || now - lastOrphanWarningNanos >= ORPHAN_WARNING_INTERVAL_NANOS) {
            lastOrphanWarningNanos = now;
            LOGGER.warning("Removed orphan custom block identity numeric_id=" + numericId + " at " + position);
        }
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

    public enum BreakBlockStatus {
        NOT_CUSTOM_BLOCK,
        CUSTOM_BLOCK_BROKEN,
        ORPHAN_BLOCK_REMOVED,
        STORE_FAILED,
        DROP_FAILED
    }

    public record BreakBlockResult(BreakBlockStatus status, String message) {
        public BreakBlockResult {
            Objects.requireNonNull(status, "status");
            message = Objects.requireNonNull(message, "message");
        }

        public boolean handledCustomBlockIdentity() {
            return status == BreakBlockStatus.CUSTOM_BLOCK_BROKEN
                    || status == BreakBlockStatus.ORPHAN_BLOCK_REMOVED
                    || status == BreakBlockStatus.DROP_FAILED;
        }

        public static BreakBlockResult notCustomBlock() {
            return new BreakBlockResult(BreakBlockStatus.NOT_CUSTOM_BLOCK, "Block is not custom.");
        }

        public static BreakBlockResult customBlockBroken() {
            return new BreakBlockResult(BreakBlockStatus.CUSTOM_BLOCK_BROKEN, "Custom block broken.");
        }

        public static BreakBlockResult orphanBlockRemoved() {
            return new BreakBlockResult(BreakBlockStatus.ORPHAN_BLOCK_REMOVED, "Orphan custom block identity removed.");
        }

        public static BreakBlockResult storeFailed(String message) {
            return new BreakBlockResult(BreakBlockStatus.STORE_FAILED, message);
        }

        public static BreakBlockResult dropFailed(String message) {
            return new BreakBlockResult(BreakBlockStatus.DROP_FAILED, message);
        }
    }
}
