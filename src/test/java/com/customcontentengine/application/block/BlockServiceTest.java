package com.customcontentengine.application.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlockServiceTest {
    @Test
    void storesBlockWhenCustomItemIdMatchesBlockId() {
        FakeBlockStore blockStore = new FakeBlockStore();
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore);
        WorldPosition position = new WorldPosition("world", 10, 64, 12);

        BlockService.PlaceBlockResult result = service.handlePlace(new CustomItemId("ruby_ore"), position);

        assertEquals(BlockService.PlaceBlockStatus.SUCCESS, result.status());
        assertEquals(position, blockStore.storedPosition);
        assertEquals((short) 1, blockStore.storedNumericId);
    }

    @Test
    void rejectsUnknownCustomItem() {
        BlockService service = service(new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of()), new FakeBlockStore());

        BlockService.PlaceBlockResult result = service.handlePlace(
                new CustomItemId("ruby_ore"),
                new WorldPosition("world", 10, 64, 12));

        assertEquals(BlockService.PlaceBlockStatus.UNKNOWN_ITEM, result.status());
    }

    @Test
    void ignoresCustomItemThatIsNotABlockItem() {
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_pickaxe"))),
                new FakeBlockStore());

        BlockService.PlaceBlockResult result = service.handlePlace(
                new CustomItemId("ruby_pickaxe"),
                new WorldPosition("world", 10, 64, 12));

        assertEquals(BlockService.PlaceBlockStatus.NOT_CUSTOM_BLOCK_ITEM, result.status());
    }

    @Test
    void reportsStoreFailure() {
        FakeBlockStore blockStore = new FakeBlockStore();
        blockStore.failPut = true;
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore);

        BlockService.PlaceBlockResult result = service.handlePlace(
                new CustomItemId("ruby_ore"),
                new WorldPosition("world", 10, 64, 12));

        assertEquals(BlockService.PlaceBlockStatus.STORE_FAILED, result.status());
        assertTrue(result.message().contains("Could not store custom block ruby_ore"));
    }

    @Test
    void returnsNotCustomBlockWhenBreakPositionHasNoStoredIdentity() {
        FakeBlockStore blockStore = new FakeBlockStore();
        FakeDropPort dropPort = new FakeDropPort();
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore,
                dropPort);

        BlockService.BreakBlockResult result = service.handleBreak(new WorldPosition("world", 10, 64, 12));

        assertEquals(BlockService.BreakBlockStatus.NOT_CUSTOM_BLOCK, result.status());
        assertTrue(blockStore.removedPosition.isEmpty());
        assertTrue(dropPort.droppedPosition.isEmpty());
    }

    @Test
    void removesIdentityAndDropsConfiguredDropsWhenBreakingKnownCustomBlock() {
        FakeBlockStore blockStore = new FakeBlockStore();
        FakeDropPort dropPort = new FakeDropPort();
        WorldPosition position = new WorldPosition("world", 10, 64, 12);
        blockStore.numericId = Optional.of((short) 1);
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore,
                dropPort);

        BlockService.BreakBlockResult result = service.handleBreak(position);

        assertEquals(BlockService.BreakBlockStatus.CUSTOM_BLOCK_BROKEN, result.status());
        assertEquals(Optional.of(position), blockStore.removedPosition);
        assertEquals(Optional.of(position), dropPort.droppedPosition);
        assertEquals("ruby", dropPort.droppedTable.orElseThrow().entries().getFirst().item());
    }

    @Test
    void removesOrphanIdentityWithoutDroppingCustomDrops() {
        FakeBlockStore blockStore = new FakeBlockStore();
        FakeDropPort dropPort = new FakeDropPort();
        WorldPosition position = new WorldPosition("world", 10, 64, 12);
        blockStore.numericId = Optional.of((short) 99);
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore,
                dropPort);

        BlockService.BreakBlockResult result = service.handleBreak(position);

        assertEquals(BlockService.BreakBlockStatus.ORPHAN_BLOCK_REMOVED, result.status());
        assertEquals(Optional.of(position), blockStore.removedPosition);
        assertTrue(dropPort.droppedPosition.isEmpty());
    }

    @Test
    void reportsStoreFailureWhenBreakLookupFails() {
        FakeBlockStore blockStore = new FakeBlockStore();
        blockStore.failFind = true;
        BlockService service = service(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore);

        BlockService.BreakBlockResult result = service.handleBreak(new WorldPosition("world", 10, 64, 12));

        assertEquals(BlockService.BreakBlockStatus.STORE_FAILED, result.status());
    }

    private BlockService service(DefinitionRegistry registry, BlockStorePort blockStore) {
        return service(registry, blockStore, new FakeDropPort());
    }

    private BlockService service(DefinitionRegistry registry, BlockStorePort blockStore, DropPort dropPort) {
        return new BlockService(registry, blockStore, dropPort);
    }

    private BlockDef block(String id, short numericId) {
        return new BlockDef(
                new CustomBlockId(id),
                numericId,
                "NOTE_BLOCK",
                1001,
                "ruby_pickaxe",
                new DropTable(List.of(new DropTable.Entry("ruby", 1))));
    }

    private ItemDef item(String id) {
        return new ItemDef(new CustomItemId(id), "NOTE_BLOCK", 1001, new ToolAttributes(0.0, 1.0, 1));
    }

    private static final class FakeBlockStore implements BlockStorePort {
        private WorldPosition storedPosition;
        private short storedNumericId;
        private Optional<Short> numericId = Optional.empty();
        private Optional<WorldPosition> removedPosition = Optional.empty();
        private boolean failFind;
        private boolean failPut;

        @Override
        public Optional<Short> findNumericId(WorldPosition position) {
            if (failFind) {
                throw new IllegalStateException("store unavailable");
            }
            return numericId;
        }

        @Override
        public void put(WorldPosition position, short numericId) {
            if (failPut) {
                throw new IllegalStateException("store unavailable");
            }
            storedPosition = position;
            storedNumericId = numericId;
        }

        @Override
        public void remove(WorldPosition position) {
            removedPosition = Optional.of(position);
        }
    }

    private static final class FakeDropPort implements DropPort {
        private Optional<WorldPosition> droppedPosition = Optional.empty();
        private Optional<DropTable> droppedTable = Optional.empty();

        @Override
        public void drop(WorldPosition position, DropTable drops) {
            droppedPosition = Optional.of(position);
            droppedTable = Optional.of(drops);
        }
    }
}
