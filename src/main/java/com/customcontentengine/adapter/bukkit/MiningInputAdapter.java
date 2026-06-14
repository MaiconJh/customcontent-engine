package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.mining.MiningSessionService;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.mining.BlockTierRequirement;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningSession;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.domain.mining.ToolTier;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.ItemMetadataPort;
import com.customcontentengine.port.MiningVisualPort;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class MiningInputAdapter implements Listener {
    private final DefinitionRegistry registry;
    private final BlockStorePort blockStore;
    private final ItemMetadataPort<ItemStack> itemMetadata;
    private final MiningSessionService miningSessionService;
    private final MiningVisualPort miningVisualPort;
    private final LongSupplier clockMillis;

    public MiningInputAdapter(
            DefinitionRegistry registry,
            BlockStorePort blockStore,
            ItemMetadataPort<ItemStack> itemMetadata,
            MiningSessionService miningSessionService,
            MiningVisualPort miningVisualPort) {
        this(registry, blockStore, itemMetadata, miningSessionService, miningVisualPort, System::currentTimeMillis);
    }

    MiningInputAdapter(
            DefinitionRegistry registry,
            BlockStorePort blockStore,
            ItemMetadataPort<ItemStack> itemMetadata,
            MiningSessionService miningSessionService,
            MiningVisualPort miningVisualPort,
            LongSupplier clockMillis) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.itemMetadata = Objects.requireNonNull(itemMetadata, "itemMetadata");
        this.miningSessionService = Objects.requireNonNull(miningSessionService, "miningSessionService");
        this.miningVisualPort = Objects.requireNonNull(miningVisualPort, "miningVisualPort");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        WorldPosition target = toWorldPosition(event.getBlock().getLocation());
        Optional<BlockDef> block = customBlockAt(target);
        if (block.isEmpty() || block.get().miningHardness().isEmpty()) {
            return;
        }

        ItemStack heldItem = event.getPlayer().getInventory().getItemInMainHand();
        Optional<CustomItemId> toolId = itemMetadata.readCustomItemIdentity(heldItem);
        if (toolId.isEmpty()) {
            return;
        }

        Optional<ItemDef> tool = registry.findItem(toolId.get());
        if (tool.isEmpty() || tool.get().miningSpeed().isEmpty()) {
            return;
        }

        if (!miningSessionService.isTierEligible(tool.get(), block.get())) {
            int currentTier = tool.get().miningToolTier().map(ToolTier::level).orElse(0);
            int requiredTier = block.get().miningRequiredTier().map(BlockTierRequirement::minimumLevel).orElse(0);
            event.getPlayer().sendMessage("§cYour tool cannot mine this block (Tier " + currentTier + "/" + requiredTier + ").");
            return;
        }

        event.setCancelled(true);
        startSession(
                event.getPlayer().getUniqueId().toString(),
                target,
                toolId.get(),
                block.get().miningHardness().get(),
                tool.get().miningSpeed().get());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        String actorKey = event.getPlayer().getUniqueId().toString();
        boolean canceled = miningSessionService.cancelSession(
                actorKey,
                toWorldPosition(event.getBlock().getLocation()));
        if (canceled) {
            miningVisualPort.clearMiningVisual(actorKey);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String actorKey = event.getPlayer().getUniqueId().toString();
        if (miningSessionService.clearSession(actorKey)) {
            miningVisualPort.clearMiningVisual(actorKey);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        String actorKey = event.getPlayer().getUniqueId().toString();
        Optional<MiningSession> active = miningSessionService.getActiveSession(actorKey);
        if (active.isEmpty()) {
            return;
        }

        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        Optional<CustomItemId> newToolId = newItem == null ?
                Optional.empty() :
                itemMetadata.readCustomItemIdentity(newItem);
        if (newToolId.isEmpty() || !newToolId.get().equals(active.get().toolId())) {
            Optional<BlockDef> targetBlock = customBlockAt(active.get().target());
            Optional<ItemDef> newTool = newToolId.flatMap(registry::findItem);
            if (targetBlock.isPresent() && newTool.isPresent()) {
                miningSessionService.isTierEligible(newTool.get(), targetBlock.get());
            }
            miningSessionService.clearSession(actorKey);
            miningVisualPort.clearMiningVisual(actorKey);
        }
    }

    private Optional<BlockDef> customBlockAt(WorldPosition target) {
        return blockStore.findNumericId(target).flatMap(registry::findBlockByNumericId);
    }

    private void startSession(
            String actorKey,
            WorldPosition target,
            CustomItemId toolId,
            MiningHardness hardness,
            MiningSpeed speed) {
        miningSessionService.startSession(actorKey, target, toolId, hardness, speed, clockMillis.getAsLong());
    }

    private WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
