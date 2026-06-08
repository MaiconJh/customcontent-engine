package com.customcontentengine.adapter.bukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.application.mining.InMemoryMiningSessionRepository;
import com.customcontentengine.application.mining.MiningSessionService;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.mining.MiningDurationPolicy;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.ItemMetadataPort;
import com.customcontentengine.port.MiningVisualPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class MiningInputAdapterTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACTOR_KEY = PLAYER_ID.toString();
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 12);
    private static final CustomItemId TOOL_ID = new CustomItemId("ruby_pickaxe");

    @Test
    void blockDamageStartsSessionForCustomBlockAndCustomToolWithMining() {
        MiningSessionService service = service();
        MiningInputAdapter adapter = adapter(
                registryWithMining(),
                new FixedBlockStore(Optional.of((short) 1)),
                new FixedItemMetadataPort(Optional.of(TOOL_ID)),
                service);
        BlockDamageEvent event = blockDamageEventAt(TARGET);

        adapter.onBlockDamage(event);

        verify(event).setCancelled(true);
        assertTrue(service.getActiveSession(ACTOR_KEY).isPresent());
        assertEquals(TARGET, service.getActiveSession(ACTOR_KEY).get().target());
    }

    @Test
    void blockDamageDoesNotStartSessionWhenBlockIsNotCustom() {
        MiningSessionService service = service();
        MiningInputAdapter adapter = adapter(
                registryWithMining(),
                new FixedBlockStore(Optional.empty()),
                new FixedItemMetadataPort(Optional.of(TOOL_ID)),
                service);
        BlockDamageEvent event = blockDamageEventAt(TARGET);

        adapter.onBlockDamage(event);

        verify(event, never()).setCancelled(true);
        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
    }

    @Test
    void blockDamageDoesNotStartSessionWhenItemHasNoMiningSpeed() {
        MiningSessionService service = service();
        MiningInputAdapter adapter = adapter(
                registryWithMining(),
                new FixedBlockStore(Optional.of((short) 1)),
                new FixedItemMetadataPort(Optional.of(new CustomItemId("ruby"))),
                service);
        BlockDamageEvent event = blockDamageEventAt(TARGET);

        adapter.onBlockDamage(event);

        verify(event, never()).setCancelled(true);
        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
    }

    @Test
    void blockDamageAbortCancelsSessionForSameTarget() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        service.startSession(
                ACTOR_KEY,
                TARGET,
                TOOL_ID,
                new MiningHardness(6.0D),
                new MiningSpeed(8.0D),
                1000L);
        MiningInputAdapter adapter = adapter(
                registryWithMining(),
                new FixedBlockStore(Optional.of((short) 1)),
                new FixedItemMetadataPort(Optional.of(TOOL_ID)),
                service,
                visual);

        adapter.onBlockDamageAbort(blockDamageAbortEventAt(TARGET));

        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
        assertEquals(List.of(ACTOR_KEY), visual.clears);
    }

    @Test
    void playerQuitClearsSession() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        service.startSession(
                ACTOR_KEY,
                TARGET,
                TOOL_ID,
                new MiningHardness(6.0D),
                new MiningSpeed(8.0D),
                1000L);
        MiningInputAdapter adapter = adapter(
                registryWithMining(),
                new FixedBlockStore(Optional.of((short) 1)),
                new FixedItemMetadataPort(Optional.of(TOOL_ID)),
                service,
                visual);

        adapter.onPlayerQuit(playerQuitEvent());

        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
        assertEquals(List.of(ACTOR_KEY), visual.clears);
    }

    @Test
    void heldItemChangeCancelsSessionWhenToolChanges() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        service.startSession(
                ACTOR_KEY,
                TARGET,
                TOOL_ID,
                new MiningHardness(6.0D),
                new MiningSpeed(8.0D),
                1000L);
        MiningInputAdapter adapter = adapter(
                registryWithMining(),
                new FixedBlockStore(Optional.of((short) 1)),
                new FixedItemMetadataPort(Optional.of(new CustomItemId("ruby"))),
                service,
                visual);

        adapter.onPlayerItemHeld(playerItemHeldEvent());

        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
        assertEquals(List.of(ACTOR_KEY), visual.clears);
    }

    private static MiningInputAdapter adapter(
            DefinitionRegistry registry,
            BlockStorePort blockStore,
            ItemMetadataPort<ItemStack> itemMetadata,
            MiningSessionService service) {
        return adapter(registry, blockStore, itemMetadata, service, new CapturingVisualPort());
    }

    private static MiningInputAdapter adapter(
            DefinitionRegistry registry,
            BlockStorePort blockStore,
            ItemMetadataPort<ItemStack> itemMetadata,
            MiningSessionService service,
            MiningVisualPort visualPort) {
        return new MiningInputAdapter(registry, blockStore, itemMetadata, service, visualPort, () -> 1000L);
    }

    private static MiningSessionService service() {
        return new MiningSessionService(new InMemoryMiningSessionRepository(), MiningDurationPolicy.DEFAULT);
    }

    private static DefinitionRegistry registryWithMining() {
        return new DefinitionRegistry(
                List.of(block("ruby_ore", (short) 1, Optional.of(new MiningHardness(6.0D)))),
                List.of(
                        item("ruby", Optional.empty()),
                        item("ruby_pickaxe", Optional.of(new MiningSpeed(8.0D)))));
    }

    private static BlockDef block(String id, short numericId, Optional<MiningHardness> miningHardness) {
        return new BlockDef(
                new CustomBlockId(id),
                numericId,
                "NOTE_BLOCK",
                1001,
                "ruby_pickaxe",
                new DropTable(List.of(new DropTable.Entry("ruby", 1))),
                miningHardness);
    }

    private static ItemDef item(String id, Optional<MiningSpeed> miningSpeed) {
        return new ItemDef(
                new CustomItemId(id),
                "DIAMOND_PICKAXE",
                2001,
                new ToolAttributes(5.0D, 1.2D, 500),
                miningSpeed);
    }

    private static BlockDamageEvent blockDamageEventAt(WorldPosition position) {
        BlockDamageEvent event = mock(BlockDamageEvent.class);
        Block block = blockAt(position);
        Player player = playerWithInventory(mainHand(), null);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private static BlockDamageAbortEvent blockDamageAbortEventAt(WorldPosition position) {
        BlockDamageAbortEvent event = mock(BlockDamageAbortEvent.class);
        Block block = blockAt(position);
        Player player = playerWithInventory(mainHand(), null);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private static PlayerQuitEvent playerQuitEvent() {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        Player player = playerWithInventory(mainHand(), null);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private static PlayerItemHeldEvent playerItemHeldEvent() {
        PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
        Player player = playerWithInventory(mainHand(), mainHand());
        when(event.getPlayer()).thenReturn(player);
        when(event.getNewSlot()).thenReturn(1);
        return event;
    }

    private static Block blockAt(WorldPosition position) {
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn(position.worldName());
        when(block.getLocation()).thenReturn(new Location(world, position.x(), position.y(), position.z()));
        return block;
    }

    private static Player playerWithInventory(ItemStack mainHand, ItemStack slotItem) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(mainHand);
        when(inventory.getItem(1)).thenReturn(slotItem);
        return player;
    }

    private static ItemStack mainHand() {
        return mock(ItemStack.class);
    }

    private static final class FixedBlockStore implements BlockStorePort {
        private final Optional<Short> numericId;

        private FixedBlockStore(Optional<Short> numericId) {
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
    }

    private static final class CapturingVisualPort implements MiningVisualPort {
        private final List<String> clears = new ArrayList<>();

        @Override
        public void updateMiningStage(
                String actorKey,
                WorldPosition position,
                com.customcontentengine.domain.mining.MiningStage stage) {
        }

        @Override
        public void clearMiningVisual(String actorKey) {
            clears.add(actorKey);
        }
    }
}
