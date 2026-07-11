package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.application.mechanic.AreaBreakEventTriggerService;
import com.customcontentengine.application.mechanic.VeinMinerEventTriggerService;
import com.customcontentengine.adapter.platform.BukkitActorStateAdapter;
import com.customcontentengine.adapter.platform.BukkitEnchantmentViewAdapter;
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
    private final VeinMinerEventTriggerService veinMinerTriggerService;

    public BlockBreakAdapter(BlockService blockService) {
        this(blockService, null, null, null);
    }

    public BlockBreakAdapter(
            BlockService blockService,
            ItemMetadataPort<ItemStack> itemMetadata,
            AreaBreakEventTriggerService areaBreakTriggerService) {
        this(blockService, itemMetadata, areaBreakTriggerService, null);
    }

    public BlockBreakAdapter(
            BlockService blockService,
            ItemMetadataPort<ItemStack> itemMetadata,
            AreaBreakEventTriggerService areaBreakTriggerService,
            VeinMinerEventTriggerService veinMinerTriggerService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
        this.itemMetadata = itemMetadata;
        this.areaBreakTriggerService = areaBreakTriggerService;
        this.veinMinerTriggerService = veinMinerTriggerService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        WorldPosition origin = toWorldPosition(event.getBlock().getLocation());
        BlockService.BreakBlockResult result = blockService.handleBreak(origin);
        if (result.handledCustomBlockIdentity()) {
            event.setDropItems(false);
        }
        triggerAreaBreakIfConfigured(event, origin);
        triggerVeinMinerIfConfigured(event, origin);
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

    private void triggerVeinMinerIfConfigured(BlockBreakEvent event, WorldPosition origin) {
        if (itemMetadata == null || veinMinerTriggerService == null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        itemMetadata.readCustomItemIdentity(item)
                .ifPresent(itemId -> veinMinerTriggerService.trigger(
                        itemId,
                        origin,
                        event.getPlayer().getUniqueId().toString(),
                        new BukkitEnchantmentViewAdapter(item),
                        new BukkitActorStateAdapter(event.getPlayer())));
    }

    private WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
