package com.customcontentengine.adapter.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.application.mining.MiningRuntimeProcessor;
import com.customcontentengine.port.PeriodicSchedulerPort;
import org.junit.jupiter.api.Test;

class MiningProcessingDriverTest {

    private static final class FakePeriodicSchedulerPort implements PeriodicSchedulerPort {
        private Runnable capturedTask;
        private final PeriodicSchedulerPort.ScheduledTask task = mock(PeriodicSchedulerPort.ScheduledTask.class);
        private long lastInitialDelay = -1;
        private long lastPeriod = -1;

        @Override
        public ScheduledTask scheduleAtFixedRate(Runnable runnable, long initialDelay, long period) {
            this.capturedTask = runnable;
            lastInitialDelay = initialDelay;
            lastPeriod = period;
            return task;
        }

        void tick() {
            capturedTask.run();
        }
    }

    private MiningRuntimeProcessor fakeProcessor() {
        MiningRuntimeProcessor processor = mock(MiningRuntimeProcessor.class);
        when(processor.processActiveSessions(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(0);
        return processor;
    }

    @Test
    void startSchedulesTaskAtConfiguredPeriod() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        MiningProcessingDriver driver = new MiningProcessingDriver(
                scheduler, fakeProcessor(), 64, 2L);

        driver.start();

        assertEquals(2L, scheduler.lastInitialDelay);
        assertEquals(2L, scheduler.lastPeriod);
    }

    @Test
    void startIsIdempotent() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        MiningRuntimeProcessor processor = fakeProcessor();
        MiningProcessingDriver driver = new MiningProcessingDriver(scheduler, processor, 64, 2L);

        driver.start();
        driver.start();

        verify(scheduler.task, never()).cancel();
    }

    @Test
    void stopCancelsScheduledTask() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        MiningProcessingDriver driver = new MiningProcessingDriver(scheduler, fakeProcessor(), 64, 2L);

        driver.start();
        driver.stop();

        verify(scheduler.task).cancel();
    }

    @Test
    void stopIsIdempotent() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        MiningProcessingDriver driver = new MiningProcessingDriver(scheduler, fakeProcessor(), 64, 2L);

        driver.start();
        driver.stop();
        driver.stop();
    }

    @Test
    void scheduledTaskExecutesProcessor() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        MiningRuntimeProcessor processor = fakeProcessor();
        MiningProcessingDriver driver = new MiningProcessingDriver(scheduler, processor, 64, 2L);

        driver.start();
        scheduler.tick();

        verify(processor, atLeastOnce()).processActiveSessions(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsNonPositiveMaxSessions() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        assertThrows(IllegalArgumentException.class, () -> new MiningProcessingDriver(
                scheduler, fakeProcessor(), 0, 2L));
    }

    @Test
    void rejectsNonPositivePeriod() {
        FakePeriodicSchedulerPort scheduler = new FakePeriodicSchedulerPort();
        assertThrows(IllegalArgumentException.class, () -> new MiningProcessingDriver(
                scheduler, fakeProcessor(), 64, 0L));
    }

    @Test
    void requiresScheduler() {
        assertThrows(NullPointerException.class, () -> new MiningProcessingDriver(
                null, fakeProcessor(), 64, 2L));
    }
}
