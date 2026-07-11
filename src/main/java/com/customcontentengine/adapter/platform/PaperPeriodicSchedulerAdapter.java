package com.customcontentengine.adapter.platform;

import com.customcontentengine.port.PeriodicSchedulerPort;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Paper/Bukkit implementation of {@link PeriodicSchedulerPort} using the
 * synchronous {@code runTaskTimer}. The task runs on the server tick and must
 * not perform blocking or long-running operations.
 */
public final class PaperPeriodicSchedulerAdapter implements PeriodicSchedulerPort {
    private final Plugin plugin;

    public PaperPeriodicSchedulerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ScheduledTask scheduleAtFixedRate(Runnable task, long initialDelay, long period) {
        Objects.requireNonNull(task, "task");
        if (initialDelay < 0L) {
            throw new IllegalArgumentException("initialDelay must be non-negative");
        }
        if (period <= 0L) {
            throw new IllegalArgumentException("period must be positive");
        }
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        BukkitTask bukkitTask = scheduler.runTaskTimer(plugin, task, initialDelay, period);
        return new BukkitScheduledTask(bukkitTask);
    }

    private static final class BukkitScheduledTask implements ScheduledTask {
        private final BukkitTask bukkitTask;

        private BukkitScheduledTask(BukkitTask bukkitTask) {
            this.bukkitTask = Objects.requireNonNull(bukkitTask, "bukkitTask");
        }

        @Override
        public void cancel() {
            bukkitTask.cancel();
        }

        @Override
        public boolean isCancelled() {
            return bukkitTask.isCancelled();
        }
    }
}
