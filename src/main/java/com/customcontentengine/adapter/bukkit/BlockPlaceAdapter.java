package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.block.BlockService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockPlaceAdapter implements Listener {
    private final BlockService blockService;

    public BlockPlaceAdapter(BlockService blockService) {
        this.blockService = Objects.requireNonNull(blockService, "blockService");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Custom block placement identity wiring is intentionally kept out of the listener foundation.
    }
}
