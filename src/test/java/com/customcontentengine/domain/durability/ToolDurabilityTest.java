package com.customcontentengine.domain.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolDurabilityTest {
    @Test
    void rejectsNonPositiveMax() {
        assertThrows(IllegalArgumentException.class, () -> new ToolDurability(0, 0));
    }

    @Test
    void rejectsNegativeCurrent() {
        assertThrows(IllegalArgumentException.class, () -> new ToolDurability(10, -1));
    }

    @Test
    void rejectsCurrentExceedingMax() {
        assertThrows(IllegalArgumentException.class, () -> new ToolDurability(10, 15));
    }

    @Test
    void acceptsValidDurability() {
        ToolDurability durability = new ToolDurability(500, 250);
        assertEquals(500, durability.max());
        assertEquals(250, durability.current());
    }

    @Test
    void fullDurabilityIsNotBroken() {
        ToolDurability durability = new ToolDurability(500, 500);
        assertFalse(durability.isBroken());
    }

    @Test
    void zeroDurabilityIsBroken() {
        ToolDurability durability = new ToolDurability(500, 0);
        assertTrue(durability.isBroken());
    }
}