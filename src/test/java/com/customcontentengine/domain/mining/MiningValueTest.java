package com.customcontentengine.domain.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MiningValueTest {
    @Test
    void miningHardnessRequiresPositiveFiniteValue() {
        assertEquals(6.0D, new MiningHardness(6.0D).value());

        assertThrows(IllegalArgumentException.class, () -> new MiningHardness(0.0D));
        assertThrows(IllegalArgumentException.class, () -> new MiningHardness(-1.0D));
        assertThrows(IllegalArgumentException.class, () -> new MiningHardness(Double.POSITIVE_INFINITY));
    }

    @Test
    void miningSpeedRequiresPositiveFiniteValue() {
        assertEquals(8.0D, new MiningSpeed(8.0D).value());

        assertThrows(IllegalArgumentException.class, () -> new MiningSpeed(0.0D));
        assertThrows(IllegalArgumentException.class, () -> new MiningSpeed(-1.0D));
        assertThrows(IllegalArgumentException.class, () -> new MiningSpeed(Double.NaN));
    }
}
