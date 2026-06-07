package com.customcontentengine.builtin.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.application.mechanic.MechanicContextFactory;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AreaBreakMechanicTest {
    private static final WorldPosition ORIGIN = new WorldPosition("world", 10, 64, 20);

    @Test
    void descriptorHasAreaBreakId() {
        AreaBreakMechanic mechanic = new AreaBreakMechanic();

        assertEquals("area_break", mechanic.descriptor().id().value());
    }

    @Test
    void descriptorDeclaresRequiredCapabilities() {
        AreaBreakMechanic mechanic = new AreaBreakMechanic();

        assertEquals(EnumSet.of(
                Capability.BLOCK_QUERY,
                Capability.BLOCK_MUTATION,
                Capability.BUDGET_VIEW,
                Capability.COOLDOWN_VIEW,
                Capability.DROP_SINK,
                Capability.EXECUTION_ORIGIN
        ), mechanic.descriptor().requiredCapabilities());
    }

    @Test
    void descriptorIsNotReadOnly() {
        AreaBreakMechanic mechanic = new AreaBreakMechanic();

        assertEquals(false, mechanic.descriptor().readOnly());
    }

    @Test
    void rejectsWhenRequiredCapabilityIsMissing() {
        MechanicResult result = new AreaBreakMechanic().execute(contextWithout(DropSink.class));

        assertInstanceOf(MechanicResult.Rejected.class, result);
    }

    @Test
    void rejectsWhenOriginCapabilityIsMissing() {
        MechanicResult result = new AreaBreakMechanic().execute(contextWithout(ExecutionOrigin.class));

        assertInstanceOf(MechanicResult.Rejected.class, result);
    }

    @Test
    void rejectsWhenCooldownDoesNotAllowExecution() {
        AreaBreakHarness harness = new AreaBreakHarness(9, false);

        MechanicResult result = new AreaBreakMechanic().execute(harness.context());

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Cooldown rejected area_break", rejected.reason());
    }

    @Test
    void rejectsWhenBudgetIsZero() {
        AreaBreakHarness harness = new AreaBreakHarness(0, true);

        MechanicResult result = new AreaBreakMechanic().execute(harness.context());

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Budget exhausted", rejected.reason());
    }

    @Test
    void breaksUpToNinePositionsInFlatThreeByThreeArea() {
        AreaBreakHarness harness = new AreaBreakHarness(9, true);

        MechanicResult result = new AreaBreakMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(9, done.affectedBlocks());
        assertEquals(expectedArea(), harness.mutated);
    }

    @Test
    void queriesBlockBeforeMutation() {
        AreaBreakHarness harness = new AreaBreakHarness(9, true);

        new AreaBreakMechanic().execute(harness.context());

        for (WorldPosition position : expectedArea()) {
            assertTrue(harness.events.indexOf("query:" + position) < harness.events.indexOf("mutate:" + position));
        }
    }

    @Test
    void sendsDropsForMutatedBlocks() {
        AreaBreakHarness harness = new AreaBreakHarness(9, true);

        new AreaBreakMechanic().execute(harness.context());

        assertEquals(harness.mutated, harness.dropped);
    }

    @Test
    void returnsDoneWhenAllPositionsAreProcessed() {
        AreaBreakHarness harness = new AreaBreakHarness(9, true);

        MechanicResult result = new AreaBreakMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(9, done.affectedBlocks());
    }

    @Test
    void returnsPartialWhenBudgetCannotProcessAllPositions() {
        AreaBreakHarness harness = new AreaBreakHarness(4, true);

        MechanicResult result = new AreaBreakMechanic().execute(harness.context());

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class, result);
        assertEquals(4, partial.affectedBlocks());
        assertEquals(expectedArea().subList(4, 9), partial.remaining());
    }

    @Test
    void partialRemainingPositionsAreImmutable() {
        AreaBreakHarness harness = new AreaBreakHarness(4, true);

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class,
                new AreaBreakMechanic().execute(harness.context()));

        assertThrows(UnsupportedOperationException.class,
                () -> partial.remaining().add(new WorldPosition("world", 0, 0, 0)));
    }

    @Test
    void calculatesAreaFromOriginCapability() {
        WorldPosition shiftedOrigin = new WorldPosition("world", 30, 70, 40);
        AreaBreakHarness harness = new AreaBreakHarness(9, true, shiftedOrigin);

        new AreaBreakMechanic().execute(harness.context());

        assertEquals(flatArea(shiftedOrigin), harness.mutated);
    }

    @Test
    void mechanicIsStatelessAcrossExecutions() {
        AreaBreakMechanic mechanic = new AreaBreakMechanic();
        AreaBreakHarness first = new AreaBreakHarness(9, true, new WorldPosition("world", 1, 64, 1));
        AreaBreakHarness second = new AreaBreakHarness(9, true, new WorldPosition("world", 20, 64, 20));

        mechanic.execute(first.context());
        mechanic.execute(second.context());

        assertTrue(first.mutated.contains(new WorldPosition("world", 1, 64, 1)));
        assertTrue(second.mutated.contains(new WorldPosition("world", 20, 64, 20)));
    }

    private static MechanicContext contextWithout(Class<?> missingCapability) {
        AreaBreakHarness harness = new AreaBreakHarness(9, true);
        Map<Class<?>, ?> capabilities = harness.capabilities();
        return new MechanicContext() {
            @Override
            public <T> T require(Class<T> capabilityType) {
                if (capabilityType.equals(missingCapability)) {
                    throw new IllegalArgumentException("Capability is not available: " + capabilityType.getName());
                }
                return capabilityType.cast(capabilities.get(capabilityType));
            }
        };
    }

    private static List<WorldPosition> expectedArea() {
        return flatArea(ORIGIN);
    }

    private static List<WorldPosition> flatArea(WorldPosition origin) {
        List<WorldPosition> positions = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                positions.add(new WorldPosition(origin.worldName(), origin.x() + dx, origin.y(), origin.z() + dz));
            }
        }
        return positions;
    }

    private static final class AreaBreakHarness {
        private final Set<WorldPosition> customBlocks;
        private final List<WorldPosition> mutated = new ArrayList<>();
        private final List<WorldPosition> dropped = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private int remainingBudget;
        private final boolean cooldownAllowed;
        private final WorldPosition origin;

        private AreaBreakHarness(int budget, boolean cooldownAllowed) {
            this(budget, cooldownAllowed, ORIGIN);
        }

        private AreaBreakHarness(int budget, boolean cooldownAllowed, WorldPosition origin) {
            this.remainingBudget = budget;
            this.cooldownAllowed = cooldownAllowed;
            this.origin = origin;
            this.customBlocks = new HashSet<>(flatArea(origin));
        }

        private MechanicContext context() {
            return new MechanicContextFactory(capabilities()).createContext(new AreaBreakMechanic().descriptor());
        }

        private Map<Class<?>, ?> capabilities() {
            return Map.of(
                    BlockQuery.class, new FakeBlockQuery(),
                    BlockMutation.class, new FakeBlockMutation(),
                    BudgetView.class, new FakeBudgetView(),
                    CooldownView.class, new FakeCooldownView(),
                    DropSink.class, new FakeDropSink(),
                    ExecutionOrigin.class, new FakeExecutionOrigin()
            );
        }

        private final class FakeBlockQuery implements BlockQuery {
            @Override
            public Optional<Short> findCustomBlockNumericId(WorldPosition position) {
                events.add("query:" + position);
                return customBlocks.contains(position) ? Optional.of((short) 1) : Optional.empty();
            }
        }

        private final class FakeBlockMutation implements BlockMutation {
            @Override
            public void breakBlock(WorldPosition position) {
                events.add("mutate:" + position);
                mutated.add(position);
            }
        }

        private final class FakeBudgetView implements BudgetView {
            @Override
            public boolean tryConsume(WorldPosition position) {
                events.add("budget:" + position);
                if (remainingBudget <= 0) {
                    return false;
                }
                remainingBudget--;
                return true;
            }
        }

        private final class FakeCooldownView implements CooldownView {
            @Override
            public boolean canExecute() {
                events.add("cooldown");
                return cooldownAllowed;
            }
        }

        private final class FakeDropSink implements DropSink {
            @Override
            public void dropFor(WorldPosition position, short numericId) {
                events.add("drop:" + position);
                dropped.add(position);
            }
        }

        private final class FakeExecutionOrigin implements ExecutionOrigin {
            @Override
            public WorldPosition origin() {
                return origin;
            }
        }
    }
}
