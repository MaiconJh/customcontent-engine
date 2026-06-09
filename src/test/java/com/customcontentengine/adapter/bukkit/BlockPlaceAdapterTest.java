package com.customcontentengine.adapter.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.durability.ToolDurability;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BlockPlaceAdapterTest {
    @Test
    void readsCustomItemIdentityAndDelegatesPlacementWithPurePosition() {
        CapturingBlockStore blockStore = new CapturingBlockStore();
        DefinitionRegistry registry = new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore")));
        BlockPlaceAdapter adapter = new BlockPlaceAdapter(
                new BlockService(registry, blockStore, new NoopDropPort()),
                new FixedItemMetadataPort(new CustomItemId("ruby_ore")));
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        ItemStack item = mock(ItemStack.class);
        when(world.getName()).thenReturn("world");
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 12));
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getItemInHand()).thenReturn(item);

        adapter.onBlockPlace(event);

        assertEquals(new WorldPosition("world", 10, 64, 12), blockStore.position);
        assertEquals((short) 1, blockStore.numericId);
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

    private static final class CapturingBlockStore implements BlockStorePort {
        private WorldPosition position;
        private short numericId;

        @Override
        public Optional<Short> findNumericId(WorldPosition position) {
            return Optional.empty();
        }

        @Override
        public void put(WorldPosition position, short numericId) {
            this.position = position;
            this.numericId = numericId;
        }

        @Override
        public void remove(WorldPosition position) {
        }
    }

    private static final class FixedItemMetadataPort implements ItemMetadataPort<ItemStack> {
        private final CustomItemId itemId;

        private FixedItemMetadataPort(CustomItemId itemId) {
            this.itemId = itemId;
        }

        @Override
        public ItemStack createCustomItem(ItemDef definition) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public ItemStack applyCustomItemIdentity(ItemStack item, CustomItemId id) {
            return item;
        }

        @Override
        public Optional<CustomItemId> readCustomItemIdentity(ItemStack item) {
            return Optional.of(itemId);
        }

        @Override
        public ToolDurability initialDurabilityFor(int max) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'initialDurabilityFor'");
        }

        @Override
        public Optional<ToolDurability> readCurrentDurability(ItemStack item, int max) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'readCurrentDurability'");
        }

        @Override
        public ItemStack writeCurrentDurability(ItemStack item, ToolDurability durability) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'writeCurrentDurability'");
        }
    }

    private static final class NoopDropPort implements DropPort {
        @Override
        public void drop(WorldPosition position, DropTable drops) {
        }
    }
}
