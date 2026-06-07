package com.customcontentengine.adapter.platform;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.SchedulerPort;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class PaperSchedulerAdapter implements SchedulerPort {
    private final Plugin plugin;

    public PaperSchedulerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runOnRegion(WorldPosition position, Runnable task) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task);
    }
}
