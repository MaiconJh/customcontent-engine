package com.customcontentengine.builtin.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.customcontentengine.application.mechanic.MechanicContextFactory;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.ActorState;
import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import com.customcontentengine.internalapi.mechanic.capability.MechanicArguments;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VeinMinerMechanicTest {
    private static final WorldPosition ORIGIN = new WorldPosition("world", 10, 64, 20);
    private static final short CUSTOM_BLOCK_ID = (short) 100;

    @Test
    void descriptorHasVeinMinerId() {
        VeinMinerMechanic mechanic = new VeinMinerMechanic();

        assertEquals("vein_miner", mechanic.descriptor().id().value());
    }

    @Test
    void descriptorDeclaresRequiredCapabilities() {
        VeinMinerMechanic mechanic = new VeinMinerMechanic();

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
        VeinMinerMechanic mechanic = new VeinMinerMechanic();

        assertEquals(false, mechanic.descriptor().readOnly());
    }

    @Test
    void rejectsWhenRequiredCapabilityIsMissing() {
        MechanicResult result = new VeinMinerMechanic().execute(contextWithout(BlockQuery.class));

        assertInstanceOf(MechanicResult.Rejected.class, result);
    }

    @Test
    void rejectsWhenOriginCapabilityIsMissing() {
        MechanicResult result = new VeinMinerMechanic().execute(contextWithout(ExecutionOrigin.class));

        assertInstanceOf(MechanicResult.Rejected.class, result);
    }

    @Test
    void rejectsWhenCooldownDoesNotAllowExecution() {
        VeinMinerHarness harness = new VeinMinerHarness(10, false, 3);

        MechanicResult result = new VeinMinerMechanic().execute(harness.context());

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Cooldown rejected vein_miner", rejected.reason());
    }

    @Test
    void rejectsWhenBudgetIsZero() {
        VeinMinerHarness harness = new VeinMinerHarness(0, true, 3);

        MechanicResult result = new VeinMinerMechanic().execute(harness.context());

        assertInstanceOf(MechanicResult.Rejected.class, result);
    }

    @Test
    void rejectsWhenOriginBlockNotCustom() {
        List<WorldPosition> mutated = new ArrayList<>();
        Map<Class<?>, Object> capabilities = Map.of(
                BlockQuery.class, (BlockQuery) pos -> Optional.empty(),
                BlockMutation.class, (BlockMutation) pos -> mutated.add(pos),
                BudgetView.class, (BudgetView) pos -> true,
                CooldownView.class, (CooldownView) () -> true,
                DropSink.class, (DropSink) (pos, id) -> {},
                ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN
        );
        MechanicContext ctx = new MechanicContextFactory(capabilities).createContext(new VeinMinerMechanic().descriptor());

        MechanicResult result = new VeinMinerMechanic().execute(ctx);

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Origin block is not a custom block", rejected.reason());
    }

    @Test
    void breaksLinearVeinOfSameTypeBlocks() {
        VeinMinerHarness harness = new VeinMinerHarness(10, true, 4);

        MechanicResult result = new VeinMinerMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(4, done.affectedBlocks());
    }

    @Test
    void respectsMaxBlocksLimit() {
        VeinMinerHarness harness = new VeinMinerHarness(10, true, 5, (short) 1);

        MechanicResult result = new VeinMinerMechanic(2, 10).execute(harness.context());

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class, result);
        assertEquals(2, partial.affectedBlocks());
        assertTrue(partial.affectedBlocks() < harness.veinLength);
    }

    @Test
    void respectsMaxDepthLimit() {
        VeinMinerHarness harness = new VeinMinerHarness(10, true, 5, (short) 1);

        MechanicResult result = new VeinMinerMechanic(100, 1).execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(2, done.affectedBlocks());
    }

    @Test
    void returnsPartialWhenBudgetCannotProcessAllPositions() {
        VeinMinerHarness harness = new VeinMinerHarness(2, true, 3);

        MechanicResult result = new VeinMinerMechanic().execute(harness.context());

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class, result);
        assertEquals(2, partial.affectedBlocks());
    }

    @Test
    void partialRemainingPositionsAreImmutable() {
        VeinMinerHarness harness = new VeinMinerHarness(2, true, 3);

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class,
                new VeinMinerMechanic().execute(harness.context()));

        assertThrows(UnsupportedOperationException.class,
                () -> partial.remaining().add(new WorldPosition("world", 0, 0, 0)));
    }

    @Test
    void appliesFortuneFromEnchantmentView() {
        CountingDropSink dropSink = new CountingDropSink();
        Map<Class<?>, Object> capabilities = new HashMap<>();
        capabilities.put(BlockQuery.class, new FakeBlockQueryFor(3));
        capabilities.put(BlockMutation.class, (BlockMutation) pos -> {});
        capabilities.put(BudgetView.class, (BudgetView) pos -> true);
        capabilities.put(CooldownView.class, (CooldownView) () -> true);
        capabilities.put(DropSink.class, dropSink);
        capabilities.put(ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN);
        capabilities.put(EnchantmentView.class, (EnchantmentView) key ->
                key.equals("fortune") ? OptionalInt.of(2) : OptionalInt.empty());
        MechanicContext ctx = new MechanicContextFactory(capabilities)
                .createContext(new VeinMinerMechanic().descriptor());

        new VeinMinerMechanic().execute(ctx);

        assertEquals(9, dropSink.totalDropped());
    }

    @Test
    void rejectsWhenRequireSneakAndNotSneaking() {
        Map<String, Object> arguments = Map.of("require_sneak", true);
        Map<Class<?>, Object> capabilities = new HashMap<>();
        capabilities.put(BlockQuery.class, new FakeBlockQueryFor(3));
        capabilities.put(BlockMutation.class, (BlockMutation) pos -> {});
        capabilities.put(BudgetView.class, (BudgetView) pos -> true);
        capabilities.put(CooldownView.class, (CooldownView) () -> true);
        capabilities.put(DropSink.class, (DropSink) (pos, id) -> {});
        capabilities.put(ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN);
        capabilities.put(ActorState.class, (ActorState) () -> false);
        capabilities.put(MechanicArguments.class, new FakeMechanicArguments(arguments));
        MechanicContext ctx = new MechanicContextFactory(capabilities)
                .createContext(new VeinMinerMechanic().descriptor());

        MechanicResult result = new VeinMinerMechanic().execute(ctx);

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertTrue(rejected.reason().contains("sneaking"));
    }

    @Test
    void executesWhenRequireSneakAndSneaking() {
        Map<String, Object> arguments = Map.of("require_sneak", true);
        Map<Class<?>, Object> capabilities = new HashMap<>();
        capabilities.put(BlockQuery.class, new FakeBlockQueryFor(3));
        capabilities.put(BlockMutation.class, (BlockMutation) pos -> {});
        capabilities.put(BudgetView.class, (BudgetView) pos -> true);
        capabilities.put(CooldownView.class, (CooldownView) () -> true);
        capabilities.put(DropSink.class, (DropSink) (pos, id) -> {});
        capabilities.put(ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN);
        capabilities.put(ActorState.class, (ActorState) () -> true);
        capabilities.put(MechanicArguments.class, new FakeMechanicArguments(arguments));
        MechanicContext ctx = new MechanicContextFactory(capabilities)
                .createContext(new VeinMinerMechanic().descriptor());

        MechanicResult result = new VeinMinerMechanic().execute(ctx);

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(3, done.affectedBlocks());
    }

    @Test
    void rejectsWhenRequireSneakButActorStateUnavailable() {
        Map<String, Object> arguments = Map.of("require_sneak", true);
        Map<Class<?>, Object> capabilities = new HashMap<>();
        capabilities.put(BlockQuery.class, new FakeBlockQueryFor(3));
        capabilities.put(BlockMutation.class, (BlockMutation) pos -> {});
        capabilities.put(BudgetView.class, (BudgetView) pos -> true);
        capabilities.put(CooldownView.class, (CooldownView) () -> true);
        capabilities.put(DropSink.class, (DropSink) (pos, id) -> {});
        capabilities.put(ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN);
        capabilities.put(MechanicArguments.class, new FakeMechanicArguments(arguments));
        MechanicContext ctx = new MechanicContextFactory(capabilities)
                .createContext(new VeinMinerMechanic().descriptor());

        MechanicResult result = new VeinMinerMechanic().execute(ctx);

        assertInstanceOf(MechanicResult.Rejected.class, result);
    }

    @Test
    void clampsLimitsForAllAdjacentShape() {
        Map<String, Object> arguments = Map.of("shape", "ALL_ADJACENT", "max_blocks", 512, "max_depth", 64);
        Map<Class<?>, Object> capabilities = new HashMap<>();
        capabilities.put(BlockQuery.class, new FakeBlockQueryFor(40));
        capabilities.put(BlockMutation.class, (BlockMutation) pos -> {});
        capabilities.put(BudgetView.class, (BudgetView) pos -> true);
        capabilities.put(CooldownView.class, (CooldownView) () -> true);
        capabilities.put(DropSink.class, (DropSink) (pos, id) -> {});
        capabilities.put(ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN);
        capabilities.put(MechanicArguments.class, new FakeMechanicArguments(arguments));
        MechanicContext ctx = new MechanicContextFactory(capabilities)
                .createContext(new VeinMinerMechanic().descriptor());

        MechanicResult result = new VeinMinerMechanic().execute(ctx);

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(11, done.affectedBlocks());
    }

    private static final class CountingDropSink implements DropSink {
        private int total;

        int totalDropped() {
            return total;
        }

        @Override
        public void dropFor(WorldPosition position, short numericId) {
            total += 1;
        }

        @Override
        public void dropFor(WorldPosition position, short numericId, int count) {
            total += count;
        }
    }

    private static final class FakeBlockQueryFor implements BlockQuery {
        private final Set<WorldPosition> vein;

        FakeBlockQueryFor(int length) {
            this.vein = new HashSet<>();
            for (int i = 0; i < length; i++) {
                vein.add(new WorldPosition(ORIGIN.worldName(), ORIGIN.x() + i, ORIGIN.y(), ORIGIN.z()));
            }
        }

        @Override
        public Optional<Short> findCustomBlockNumericId(WorldPosition position) {
            return vein.contains(position) ? Optional.of(CUSTOM_BLOCK_ID) : Optional.empty();
        }
    }

    @Test
    void appliesMaxBlocksFromMechanicArguments() {
        Map<String, Object> arguments = Map.of("max_blocks", 2);
        Map<Class<?>, Object> capabilities = new HashMap<>();
        capabilities.put(BlockQuery.class, new FakeBlockQueryFor(10));
        capabilities.put(BlockMutation.class, (BlockMutation) pos -> {});
        capabilities.put(BudgetView.class, (BudgetView) pos -> true);
        capabilities.put(CooldownView.class, (CooldownView) () -> true);
        capabilities.put(DropSink.class, (DropSink) (pos, id) -> {});
        capabilities.put(ExecutionOrigin.class, (ExecutionOrigin) () -> ORIGIN);
        capabilities.put(MechanicArguments.class, new FakeMechanicArguments(arguments));
        MechanicContext ctx = new MechanicContextFactory(capabilities)
                .createContext(new VeinMinerMechanic().descriptor());

        MechanicResult result = new VeinMinerMechanic().execute(ctx);

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class, result);
        assertEquals(2, partial.affectedBlocks());
    }

    private static final class FakeMechanicArguments implements MechanicArguments {
        private final Map<String, Object> arguments;

        FakeMechanicArguments(Map<String, Object> arguments) {
            this.arguments = arguments;
        }

        @Override
        public Optional<Object> get(String key) {
            return Optional.ofNullable(arguments.get(key));
        }

        @Override
        public Map<String, Object> all() {
            return arguments;
        }
    }

    private static MechanicContext contextWithout(Class<?> missingCapability) {
        VeinMinerHarness harness = new VeinMinerHarness(10, true, 3);
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

    private static final class VeinMinerHarness {
        private final Set<WorldPosition> customBlocks;
        private final List<WorldPosition> mutated = new ArrayList<>();
        private final List<WorldPosition> dropped = new ArrayList<>();
        private final int budget;
        private final boolean cooldownAllowed;
        private final WorldPosition origin;
        private final int veinLength;

        private VeinMinerHarness(int budget, boolean cooldownAllowed, int veinLength) {
            this(budget, cooldownAllowed, veinLength, CUSTOM_BLOCK_ID);
        }

        private VeinMinerHarness(int budget, boolean cooldownAllowed, int veinLength, short blockId) {
            this.budget = budget;
            this.cooldownAllowed = cooldownAllowed;
            this.origin = ORIGIN;
            this.veinLength = veinLength;
            this.customBlocks = createLinearVein(veinLength, blockId);
        }

        private MechanicContext context() {
            return new MechanicContextFactory(capabilities()).createContext(new VeinMinerMechanic().descriptor());
        }

        @SuppressWarnings("unchecked")
        private Map<Class<?>, Object> capabilities() {
            Map<Class<?>, Object> map = new HashMap<>();
            map.put(BlockQuery.class, new FakeBlockQuery());
            map.put(BlockMutation.class, new FakeBlockMutation());
            map.put(BudgetView.class, new FakeBudgetView());
            map.put(CooldownView.class, new FakeCooldownView());
            map.put(DropSink.class, new FakeDropSink());
            map.put(ExecutionOrigin.class, new FakeExecutionOrigin());
            return map;
        }

        private Set<WorldPosition> createLinearVein(int length, short blockId) {
            Set<WorldPosition> vein = new HashSet<>();
            for (int i = 0; i < length; i++) {
                vein.add(new WorldPosition(ORIGIN.worldName(), ORIGIN.x() + i, ORIGIN.y(), ORIGIN.z()));
            }
            return vein;
        }

        private final class FakeBlockQuery implements BlockQuery {
            @Override
            public Optional<Short> findCustomBlockNumericId(WorldPosition position) {
                return customBlocks.contains(position) ? Optional.of(CUSTOM_BLOCK_ID) : Optional.empty();
            }
        }

        private final class FakeBlockMutation implements BlockMutation {
            @Override
            public void breakBlock(WorldPosition position) {
                mutated.add(position);
            }
        }

        private final class FakeBudgetView implements BudgetView {
            private int remaining = budget;

            @Override
            public boolean tryConsume(WorldPosition position) {
                if (remaining <= 0) {
                    return false;
                }
                remaining--;
                return true;
            }
        }

        private final class FakeCooldownView implements CooldownView {
            @Override
            public boolean canExecute() {
                return cooldownAllowed;
            }
        }

        private final class FakeDropSink implements DropSink {
            @Override
            public void dropFor(WorldPosition position, short numericId) {
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