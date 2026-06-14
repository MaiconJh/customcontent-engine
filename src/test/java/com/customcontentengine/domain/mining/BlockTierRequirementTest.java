package com.customcontentengine.domain.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BlockTierRequirementTest {
    @Test
    void blockTierRequirementStoresPositiveMinimumLevel() {
        BlockTierRequirement requirement = new BlockTierRequirement(2);
        assertEquals(2, requirement.minimumLevel());
    }

    @Test
    void blockTierRequirementRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new BlockTierRequirement(0));
    }

    @Test
    void blockTierRequirementRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new BlockTierRequirement(-1));
    }

    @Test
    void blockTierRequirementEquality() {
        BlockTierRequirement req1 = new BlockTierRequirement(2);
        BlockTierRequirement req2 = new BlockTierRequirement(2);
        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertEquals(req1, req1);
    }

    @Test
    void blockTierRequirementInequality() {
        BlockTierRequirement req1 = new BlockTierRequirement(2);
        BlockTierRequirement req3 = new BlockTierRequirement(3);
        assertNotEquals(req1, req3);
    }
}
