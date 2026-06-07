package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.application.mechanic.AreaBreakEventTriggerService;
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
    private final AreaBreakEventTriggerService areaBreakTriggerService;

    public BlockBreakAdapter(BlockService blockService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
        this.itemMetadata = null;
        this.areaBreakTriggerService = null;
    }

    public BlockBreakAdapter(
            BlockService blockService,
            ItemMetadataPort<ItemStack> itemMetadata,
            AreaBreakEventTriggerService areaBreakTriggerService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
        this.itemMetadata = Objects.requireNonNull(itemMetadata, "itemMetadata");
        this.areaBreakTriggerService = Objects.requireNonNull(areaBreakTriggerService, "areaBreakTriggerService");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        WorldPosition origin = toWorldPosition(event.getBlock().getLocation());
        BlockService.BreakBlockResult result = blockService.handleBreak(origin);
        if (result.handledCustomBlockIdentity()) {
            event.setDropItems(false);
        }
        triggerAreaBreakIfConfigured(event, origin);
    }

    private void triggerAreaBreakIfConfigured(BlockBreakEvent event, WorldPosition origin) {
        if (itemMetadata == null || areaBreakTriggerService == null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        itemMetadata.readCustomItemIdentity(item)
                .ifPresent(itemId -> areaBreakTriggerService.trigger(
                        itemId,
                        origin,
                        event.getPlayer().getUniqueId().toString()));
    }

    private WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
