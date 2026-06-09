package com.customcontentengine.domain.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolDurabilityDefinitionTest {
    @Test
    void rejectsNonPositiveMax() {
        assertThrows(IllegalArgumentException.class, () -> new ToolDurabilityDefinition(0, 1, ToolBreakPolicy.BREAK));
    }

    @Test
    void rejectsNegativeDamage() {
        assertThrows(IllegalArgumentException.class, () -> new ToolDurabilityDefinition(100, -1, ToolBreakPolicy.BREAK));
    }

    @Test
    void acceptsValidDefinition() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 1, ToolBreakPolicy.BREAK);
        assertEquals(500, def.max());
        assertEquals(1, def.damageOnCustomBlockBreak());
        assertEquals(ToolBreakPolicy.BREAK, def.breakPolicy());
    }

    @Test
    void initialDurabilityStartsAtMax() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 1, ToolBreakPolicy.BREAK);
        ToolDurability initial = def.initialDurability();
        assertEquals(500, initial.max());
        assertEquals(500, initial.current());
    }

    @Test
    void appliesWearReducingCurrentByDamage() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 10, ToolBreakPolicy.BREAK);
        ToolDurability current = new ToolDurability(500, 100);
        ToolWearResult result = def.applyWear(current);
        assertEquals(90, result.newDurability().current());
        assertFalse(result.shouldBreak());
    }

    @Test
    void appliesWearNotBelowZero() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 100, ToolBreakPolicy.BREAK);
        ToolDurability current = new ToolDurability(500, 5);
        ToolWearResult result = def.applyWear(current);
        assertEquals(0, result.newDurability().current());
        assertTrue(result.shouldBreak());
    }

    @Test
    void zeroDamageDoesNotChangeDurability() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 0, ToolBreakPolicy.BREAK);
        ToolDurability current = new ToolDurability(500, 100);
        ToolWearResult result = def.applyWear(current);
        assertEquals(100, result.newDurability().current());
        assertFalse(result.shouldBreak());
    }

    @Test
    void breakPolicyBreakRemovesToolAtZero() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 500, ToolBreakPolicy.BREAK);
        ToolDurability current = new ToolDurability(500, 500);
        ToolWearResult result = def.applyWear(current);
        assertTrue(result.shouldBreak());
    }

    @Test
    void breakPolicyPreserveKeepsToolAtZero() {
        ToolDurabilityDefinition def = new ToolDurabilityDefinition(500, 500, ToolBreakPolicy.PRESERVE);
        ToolDurability current = new ToolDurability(500, 500);
        ToolWearResult result = def.applyWear(current);
        assertFalse(result.shouldBreak());
        assertEquals(0, result.newDurability().current());
    }
}