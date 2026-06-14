package com.customcontentengine.domain.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ToolTierTest {
    @Test
    void toolTierStoresPositiveLevel() {
        ToolTier tier = new ToolTier(3);
        assertEquals(3, tier.level());
    }

    @Test
    void toolTierRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new ToolTier(0));
    }

    @Test
    void toolTierRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new ToolTier(-1));
    }

    @Test
    void toolTierEquality() {
        ToolTier tier1 = new ToolTier(2);
        ToolTier tier2 = new ToolTier(2);
        assertEquals(tier1, tier2);
        assertEquals(tier1.hashCode(), tier2.hashCode());
        assertEquals(tier1, tier1);
    }

    @Test
    void toolTierInequality() {
        ToolTier tier1 = new ToolTier(2);
        ToolTier tier3 = new ToolTier(3);
        assertNotEquals(tier1, tier3);
    }
}
