package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockPlaceAdapter implements Listener {
    private final BlockService blockService;
    private final ItemMetadataPort<ItemStack> itemMetadata;

    public BlockPlaceAdapter(BlockService blockService, ItemMetadataPort<ItemStack> itemMetadata) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
        this.itemMetadata = Objects.requireNonNull(itemMetadata, "itemMetadata");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        itemMetadata.readCustomItemIdentity(event.getItemInHand())
                .ifPresent(itemId -> blockService.handlePlace(
                        itemId,
                        toWorldPosition(event.getBlockPlaced().getLocation())));
    }

    private WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
