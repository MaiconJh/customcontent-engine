package com.customcontentengine.port;

/**
 * Port for periodic (fixed-rate) task scheduling, decoupled from any specific
 * platform scheduler. Used by infrastructure orchestrators such as
 * {@code MiningProcessingDriver} that need to run repetitive work on the server
 * tick without depending directly on Bukkit/Paper.
 */
public interface PeriodicSchedulerPort {

    /**
     * Schedules a task to run repeatedly at a fixed rate.
     *
     * @param task          the task to execute (must not be blocking)
     * @param initialDelay  delay in server ticks before first execution
     * @param period        interval in server ticks between executions
     * @return a handle that can be used to cancel the task
     */
    ScheduledTask scheduleAtFixedRate(Runnable task, long initialDelay, long period);

    /**
     * Represents a scheduled task that can be cancelled.
     */
    interface ScheduledTask {
        void cancel();

        boolean isCancelled();
    }
}
