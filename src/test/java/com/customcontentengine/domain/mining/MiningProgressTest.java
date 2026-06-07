package com.customcontentengine.domain.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiningProgressTest {
    @Test
    void durationIncreasesWithHardness() {
        MiningDurationPolicy policy = new MiningDurationPolicy(1000L);
        MiningSpeed speed = new MiningSpeed(2.0D);

        long softDuration = policy.expectedDurationMillis(new MiningHardness(2.0D), speed);
        long hardDuration = policy.expectedDurationMillis(new MiningHardness(6.0D), speed);

        assertTrue(hardDuration > softDuration);
    }

    @Test
    void durationDecreasesWithSpeed() {
        MiningDurationPolicy policy = new MiningDurationPolicy(1000L);
        MiningHardness hardness = new MiningHardness(6.0D);

        long slowDuration = policy.expectedDurationMillis(hardness, new MiningSpeed(2.0D));
        long fastDuration = policy.expectedDurationMillis(hardness, new MiningSpeed(6.0D));

        assertTrue(fastDuration < slowDuration);
    }

    @Test
    void initialProgressIsZero() {
        assertEquals(0.0D, MiningProgress.at(1000L, 1000L, 2000L).value());
    }

    @Test
    void progressUsesAbsoluteTime() {
        assertEquals(0.5D, MiningProgress.at(1000L, 2000L, 2000L).value());
    }

    @Test
    void finalProgressIsOne() {
        MiningProgress progress = MiningProgress.at(1000L, 3000L, 2000L);

        assertEquals(1.0D, progress.value());
        assertTrue(progress.complete());
    }

    @Test
    void progressDoesNotExceedOne() {
        assertEquals(1.0D, MiningProgress.at(1000L, 9000L, 2000L).value());
    }

    @Test
    void progressDoesNotGoBelowZero() {
        assertEquals(0.0D, MiningProgress.at(1000L, 500L, 2000L).value());
    }

    @Test
    void visualStageChangesOnlyAfterThresholdCrossing() {
        MiningStage initial = MiningStage.fromProgress(new MiningProgress(0.09D));
        MiningStage sameThreshold = MiningStage.fromProgress(new MiningProgress(0.099D));
        MiningStage nextThreshold = MiningStage.fromProgress(new MiningProgress(0.10D));

        assertEquals(initial, sameThreshold);
        assertEquals(new MiningStage(1), nextThreshold);
        assertFalse(initial.equals(nextThreshold));
    }

    @Test
    void invalidProgressAndDurationValuesFail() {
        assertThrows(IllegalArgumentException.class, () -> new MiningProgress(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MiningProgress.at(0L, 1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new MiningDurationPolicy(0L));
        assertThrows(IllegalArgumentException.class, () -> new MiningStage(-1));
        assertThrows(IllegalArgumentException.class, () -> new MiningStage(10));
    }
}
