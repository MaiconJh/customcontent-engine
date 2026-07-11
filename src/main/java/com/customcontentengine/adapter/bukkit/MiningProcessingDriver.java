package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.mining.MiningRuntimeProcessor;
import com.customcontentengine.port.PeriodicSchedulerPort;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Infrastructure orchestrator that periodically drives active mining session
 * processing. Scheduling is delegated to {@link PeriodicSchedulerPort} so the
 * driver stays free of direct Bukkit dependencies.
 */
public final class MiningProcessingDriver {
    private final PeriodicSchedulerPort scheduler;
    private final MiningRuntimeProcessor processor;
    private final LongSupplier clockMillis;
    private final int maxSessionsPerRun;
    private final long periodTicks;
    private PeriodicSchedulerPort.ScheduledTask task;

    public MiningProcessingDriver(
            PeriodicSchedulerPort scheduler,
            MiningRuntimeProcessor processor,
            int maxSessionsPerRun,
            long periodTicks) {
        this(scheduler, processor, System::currentTimeMillis, maxSessionsPerRun, periodTicks);
    }

    MiningProcessingDriver(
            PeriodicSchedulerPort scheduler,
            MiningRuntimeProcessor processor,
            LongSupplier clockMillis,
            int maxSessionsPerRun,
            long periodTicks) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
        task = scheduler.scheduleAtFixedRate(
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
