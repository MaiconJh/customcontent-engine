package com.customcontentengine.internalapi.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MechanicContractTest {
    @Test
    void descriptorRequiresValidId() {
        assertThrows(NullPointerException.class, () -> new MechanicDescriptor(null, Set.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new MechanicId("Area Break"));

        MechanicDescriptor descriptor = new MechanicDescriptor(new MechanicId("area_break"), Set.of(), false);

        assertEquals("area_break", descriptor.id().value());
    }

    @Test
    void descriptorStoresImmutableRequiredCapabilities() {
        Set<Capability> capabilities = EnumSet.of(Capability.BLOCK_QUERY, Capability.BUDGET_VIEW);

        MechanicDescriptor descriptor = new MechanicDescriptor(new MechanicId("area_break"), capabilities, false);
        capabilities.add(Capability.DROP_SINK);

        assertEquals(Set.of(Capability.BLOCK_QUERY, Capability.BUDGET_VIEW), descriptor.requiredCapabilities());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.requiredCapabilities().add(Capability.DROP_SINK));
    }

    @Test
    void doneStoresAffectedBlocks() {
        MechanicResult result = new MechanicResult.Done(3);

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(3, done.affectedBlocks());
    }

    @Test
    void partialStoresAffectedBlocksAndImmutableRemainingPositions() {
        List<WorldPosition> remaining = new java.util.ArrayList<>();
        remaining.add(new WorldPosition("world", 1, 64, 1));

        MechanicResult.Partial partial = new MechanicResult.Partial(2, remaining);
        remaining.add(new WorldPosition("world", 2, 64, 2));

        assertEquals(2, partial.affectedBlocks());
        assertEquals(List.of(new WorldPosition("world", 1, 64, 1)), partial.remaining());
        assertThrows(UnsupportedOperationException.class, () -> partial.remaining().add(new WorldPosition("world", 3, 64, 3)));
    }

    @Test
    void rejectedRequiresNonBlankReason() {
        assertThrows(NullPointerException.class, () -> new MechanicResult.Rejected(null));
        assertThrows(IllegalArgumentException.class, () -> new MechanicResult.Rejected(" "));

        MechanicResult.Rejected rejected = new MechanicResult.Rejected("cooldown");

        assertEquals("cooldown", rejected.reason());
    }

    @Test
    void capabilityContainsAdrValues() {
        assertTrue(EnumSet.allOf(Capability.class).containsAll(Set.of(
                Capability.BLOCK_QUERY,
                Capability.BLOCK_MUTATION,
                Capability.BLOCK_PLACEMENT,
                Capability.BUDGET_VIEW,
                Capability.COOLDOWN_VIEW,
                Capability.DROP_SINK,
                Capability.EXECUTION_ORIGIN,
                Capability.ENCHANTMENT_VIEW,
                Capability.MECHANIC_ARGUMENTS,
                Capability.MECHANIC_CONFIG,
                Capability.ACTOR_STATE
        )));
        assertEquals(11, Capability.values().length);
    }
}
