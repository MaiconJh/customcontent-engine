package com.customcontentengine.adapter.bukkit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.application.mechanic.AreaBreakEventTriggerService;
import com.customcontentengine.domain.durability.ToolDurability;
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
import com.customcontentengine.port.ItemMetadataPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class BlockBreakAdapterTest {
    @Test
    void delegatesBreakAndDisablesVanillaDropsForCustomBlock() {
        CapturingBlockStore blockStore = new CapturingBlockStore((short) 1);
        BlockBreakAdapter adapter = new BlockBreakAdapter(new BlockService(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore,
                new NoopDropPort()));
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 12));
        when(event.getBlock()).thenReturn(block);

        adapter.onBlockBreak(event);

        verify(event).setDropItems(false);
    }

    @Test
    void doesNotTriggerAreaBreakWhenHeldItemIsNotCustom() {
        AreaBreakEventTriggerService triggerService = mock(AreaBreakEventTriggerService.class);
        BlockBreakAdapter adapter = new BlockBreakAdapter(
                service(new CapturingBlockStore(Optional.empty()), new NoopDropPort()),
                new FixedItemMetadataPort(Optional.empty()),
                triggerService);
        BlockBreakEvent event = eventAt(new WorldPosition("world", 10, 64, 12));

        adapter.onBlockBreak(event);

        verifyNoInteractions(triggerService);
    }

    @Test
    void delegatesCustomHeldItemToAreaBreakTriggerService() {
        AreaBreakEventTriggerService triggerService = mock(AreaBreakEventTriggerService.class);
        BlockBreakAdapter adapter = new BlockBreakAdapter(
                service(new CapturingBlockStore(Optional.empty()), new NoopDropPort()),
                new FixedItemMetadataPort(Optional.of(new CustomItemId("ruby_pickaxe"))),
                triggerService);
        WorldPosition origin = new WorldPosition("world", 10, 64, 12);
        BlockBreakEvent event = eventAt(origin);

        adapter.onBlockBreak(event);

        verify(triggerService).trigger(new CustomItemId("ruby_pickaxe"), origin, PLAYER_ID.toString());
    }

    @Test
    void keepsOriginHandledByMvp0WhenAreaBreakToolIsUsed() {
        AreaBreakEventTriggerService triggerService = mock(AreaBreakEventTriggerService.class);
        CapturingBlockStore blockStore = new CapturingBlockStore(Optional.of((short) 1));
        CapturingDropPort dropPort = new CapturingDropPort();
        BlockBreakAdapter adapter = new BlockBreakAdapter(
                service(blockStore, dropPort),
                new FixedItemMetadataPort(Optional.of(new CustomItemId("ruby_pickaxe"))),
                triggerService);
        WorldPosition origin = new WorldPosition("world", 10, 64, 12);
        BlockBreakEvent event = eventAt(origin);

        adapter.onBlockBreak(event);

        verify(event).setDropItems(false);
        verify(triggerService).trigger(new CustomItemId("ruby_pickaxe"), origin, PLAYER_ID.toString());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(origin), blockStore.removed);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(origin), dropPort.positions);
    }

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static BlockBreakEvent eventAt(WorldPosition position) {
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        when(world.getName()).thenReturn(position.worldName());
        when(block.getLocation()).thenReturn(new Location(world, position.x(), position.y(), position.z()));
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        return event;
    }

    private static BlockService service(BlockStorePort blockStore, DropPort dropPort) {
        return new BlockService(
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 1)), List.of(item("ruby_ore"))),
                blockStore,
                dropPort);
    }

    private static BlockDef block(String id, short numericId) {
        return new BlockDef(
                new CustomBlockId(id),
                numericId,
                "NOTE_BLOCK",
                1001,
                "ruby_pickaxe",
                new DropTable(List.of(new DropTable.Entry("ruby", 1))));
    }

    private static ItemDef item(String id) {
        return new ItemDef(new CustomItemId(id), "NOTE_BLOCK", 1001, new ToolAttributes(0.0, 1.0, 1), Optional.empty(), Optional.empty());
    }

    private static final class CapturingBlockStore implements BlockStorePort {
        private final Optional<Short> numericId;
        private final List<WorldPosition> removed = new ArrayList<>();

        private CapturingBlockStore(short numericId) {
            this(Optional.of(numericId));
        }

        private CapturingBlockStore(Optional<Short> numericId) {
            this.numericId = numericId;
        }

        @Override
        public Optional<Short> findNumericId(WorldPosition position) {
            return numericId;
        }

        @Override
        public void put(WorldPosition position, short numericId) {
        }

        @Override
        public void remove(WorldPosition position) {
            removed.add(position);
        }
    }

    private static final class CapturingDropPort implements DropPort {
        private final List<WorldPosition> positions = new ArrayList<>();

        @Override
        public void drop(WorldPosition position, DropTable drops) {
            positions.add(position);
        }
    }

    private static final class NoopDropPort implements DropPort {
        @Override
        public void drop(WorldPosition position, DropTable drops) {
        }
    }

    private static final class FixedItemMetadataPort implements ItemMetadataPort<ItemStack> {
        private final Optional<CustomItemId> itemId;

        private FixedItemMetadataPort(Optional<CustomItemId> itemId) {
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
            return itemId;
        }

        @Override
        public ToolDurability initialDurabilityFor(int max) {
            return new ToolDurability(max, max);
        }

        @Override
        public Optional<ToolDurability> readCurrentDurability(ItemStack item, int max) {
            return Optional.of(new ToolDurability(max, max));
        }

        @Override
        public ItemStack writeCurrentDurability(ItemStack item, ToolDurability durability) {
            return item;
        }
    }
}
