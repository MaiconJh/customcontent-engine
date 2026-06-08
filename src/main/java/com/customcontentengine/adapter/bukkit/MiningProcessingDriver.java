package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.mining.MiningRuntimeProcessor;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class MiningProcessingDriver {
    private final Plugin plugin;
    private final MiningRuntimeProcessor processor;
    private final LongSupplier clockMillis;
    private final int maxSessionsPerRun;
    private final long periodTicks;
    private BukkitTask task;

    public MiningProcessingDriver(
            Plugin plugin,
            MiningRuntimeProcessor processor,
            int maxSessionsPerRun,
            long periodTicks) {
        this(plugin, processor, System::currentTimeMillis, maxSessionsPerRun, periodTicks);
    }

    MiningProcessingDriver(
            Plugin plugin,
            MiningRuntimeProcessor processor,
            LongSupplier clockMillis,
            int maxSessionsPerRun,
            long periodTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        if (maxSessionsPerRun <= 0) {
            throw new IllegalArgumentException("maxSessionsPerRun must be positive");
        }
        if (periodTicks <= 0L) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        this.maxSessionsPerRun = maxSessionsPerRun;
        this.periodTicks = periodTicks;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> processor.processActiveSessions(clockMillis.getAsLong(), maxSessionsPerRun),
                periodTicks,
                periodTicks);
    }

    public void stop() {
        if (task == null) {
            return;
        }
        task.cancel();
        task = null;
    }
}
