package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlockBreakAdapter implements Listener {
    private final BlockService blockService;

    public BlockBreakAdapter(BlockService blockService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        blockService.handleBreak(toWorldPosition(event.getBlock().getLocation()));
    }

    private WorldPosition toWorldPosition(org.bukkit.Location location) {
        return new WorldPosition(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
