package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.application.mechanic.MechanicEventTriggerService;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockBreakAdapter implements Listener {
    private final BlockService blockService;
    private final ItemMetadataPort<ItemStack> itemMetadata;
    private final MechanicEventTriggerService mechanicTriggerService;

    public BlockBreakAdapter(BlockService blockService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
        this.itemMetadata = null;
        this.mechanicTriggerService = null;
    }

    public BlockBreakAdapter(
            BlockService blockService,
            ItemMetadataPort<ItemStack> itemMetadata,
            MechanicEventTriggerService mechanicTriggerService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
        this.itemMetadata = Objects.requireNonNull(itemMetadata, "itemMetadata");
        this.mechanicTriggerService = Objects.requireNonNull(mechanicTriggerService, "mechanicTriggerService");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        WorldPosition origin = toWorldPosition(event.getBlock().getLocation());
        BlockService.BreakBlockResult result = blockService.handleBreak(origin);
        if (result.handledCustomBlockIdentity()) {
            event.setDropItems(false);
        }
        triggerMechanicIfConfigured(event, origin);
    }

    private void triggerMechanicIfConfigured(BlockBreakEvent event, WorldPosition origin) {
        if (itemMetadata == null || mechanicTriggerService == null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        itemMetadata.readCustomItemIdentity(item)
                .ifPresent(itemId -> mechanicTriggerService.trigger(itemId, origin, event.getPlayer().getUniqueId().toString()));
    }

    private WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
