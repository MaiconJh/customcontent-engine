package com.customcontentengine.builtin.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.application.mechanic.MechanicContextFactory;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockPlacement;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.internalapi.mechanic.capability.MechanicConfig;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockTransformMechanicTest {
    private static final WorldPosition ORIGIN = new WorldPosition("world", 10, 64, 20);

    @Test
    void descriptorHasBlockTransformId() {
        BlockTransformMechanic mechanic = new BlockTransformMechanic();

        assertEquals("block_transform", mechanic.descriptor().id().value());
    }

    @Test
    void descriptorDeclaresRequiredCapabilities() {
        BlockTransformMechanic mechanic = new BlockTransformMechanic();

        assertEquals(EnumSet.of(
                Capability.BLOCK_QUERY,
                Capability.BLOCK_MUTATION,
                Capability.BLOCK_PLACEMENT,
                Capability.BUDGET_VIEW,
                Capability.COOLDOWN_VIEW,
                Capability.DROP_SINK,
                Capability.EXECUTION_ORIGIN,
                Capability.MECHANIC_CONFIG
        ), mechanic.descriptor().requiredCapabilities());
    }

    @Test
    void descriptorIsNotReadOnly() {
        BlockTransformMechanic mechanic = new BlockTransformMechanic();

        assertEquals(false, mechanic.descriptor().readOnly());
    }

    @Test
    void rejectsWhenMissingToBlockArgument() {
        BlockTransformHarness harness = new BlockTransformHarness(true, false, Map.of());

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        assertInstanceOf(MechanicResult.Rejected.class, result);
        MechanicResult.Rejected rejected = (MechanicResult.Rejected) result;
        assertEquals("Missing required argument: to_block", rejected.reason());
    }

    @Test
    void rejectsWhenCooldownDoesNotAllowExecution() {
        BlockTransformHarness harness = new BlockTransformHarness(false, false, Map.of("to_block", "42"));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Cooldown rejected block_transform", rejected.reason());
    }

    @Test
    void rejectsWhenBudgetIsExhausted() {
        BlockTransformHarness harness = new BlockTransformHarness(true, true, Map.of("to_block", "42", "consume_budget", true));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Budget exhausted", rejected.reason());
    }

    @Test
    void returnsDoneZeroWhenNoCustomBlockAtOrigin() {
        BlockTransformHarness harness = new BlockTransformHarness(true, false, Map.of("to_block", "1"));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(0, done.affectedBlocks());
    }

    @Test
    void transformsBlockToCustomBlock() {
        BlockTransformHarness harness = new BlockTransformHarness(true, true, Map.of("to_block", "42"));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(1, done.affectedBlocks());
        assertEquals(Set.of(ORIGIN), harness.mutated);
        assertEquals(Set.of(ORIGIN), harness.placedBlocks);
    }

    @Test
    void transformsBlockToMaterial() {
        BlockTransformHarness harness = new BlockTransformHarness(true, true, Map.of("to_block", "stone"));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(1, done.affectedBlocks());
        assertEquals(Set.of(ORIGIN), harness.mutated);
        assertEquals(Set.of(ORIGIN), harness.placedMaterials);
    }

    @Test
    void dropsOriginalBlockWhenConfigured() {
        BlockTransformHarness harness = new BlockTransformHarness(true, true,
                Map.of("to_block", "42", "drop_original", true));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(1, done.affectedBlocks());
        assertEquals(Set.of(ORIGIN), harness.dropped);
    }

    @Test
    void doesNotDropOriginalBlockWhenNotConfigured() {
        BlockTransformHarness harness = new BlockTransformHarness(true, true,
                Map.of("to_block", "42", "drop_original", false));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(1, done.affectedBlocks());
        assertTrue(harness.dropped.isEmpty());
    }

    @Test
    void doesNotConsumeBudgetWhenConfiguredFalse() {
        BlockTransformHarness harness = new BlockTransformHarness(true, true,
                Map.of("to_block", "42", "consume_budget", false));

        MechanicResult result = new BlockTransformMechanic().execute(harness.context());

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(1, done.affectedBlocks());
    }

    private static final class BlockTransformHarness {
        private final Set<WorldPosition> customBlocks = new HashSet<>();
        private final Set<WorldPosition> mutated = new HashSet<>();
        private final Set<WorldPosition> placedBlocks = new HashSet<>();
        private final Set<WorldPosition> placedMaterials = new HashSet<>();
        private final Set<WorldPosition> dropped = new HashSet<>();
        private final boolean cooldownAllowed;
        private final WorldPosition origin;
        private final Map<String, Object> config;

        private BlockTransformHarness(boolean cooldownAllowed, boolean hasCustomBlock, Map<String, Object> config) {
            this.cooldownAllowed = cooldownAllowed;
            this.origin = ORIGIN;
            this.config = config;
            if (hasCustomBlock) {
                this.customBlocks.add(ORIGIN);
            }
        }

        private MechanicContext context() {
            return new MechanicContextFactory(Map.of(
                    BlockQuery.class, blockQuery(),
                    BlockMutation.class, blockMutation(),
                    BlockPlacement.class, blockPlacement(),
                    BudgetView.class, budgetView(),
                    CooldownView.class, cooldownView(),
                    DropSink.class, dropSink(),
                    ExecutionOrigin.class, executionOrigin(),
                    MechanicConfig.class, mechanicConfig()
            )).createContext(new BlockTransformMechanic().descriptor());
        }

        private BlockQuery blockQuery() {
            return position -> customBlocks.contains(position) ? Optional.of((short) 1) : Optional.empty();
        }

        private BlockMutation blockMutation() {
            return position -> mutated.add(position);
        }

        private BlockPlacement blockPlacement() {
            return new BlockPlacement() {
                @Override
                public void placeBlock(WorldPosition position, short numericId) {
                    placedBlocks.add(position);
                }

                @Override
                public void placeMaterial(WorldPosition position, String materialName) {
                    placedMaterials.add(position);
                }
            };
        }

private BudgetView budgetView() {
    Boolean shouldConsume = (Boolean) config.getOrDefault("consume_budget", false);
        if (!shouldConsume) {
           return position -> true;
        }
        return position -> false;
    }

        private CooldownView cooldownView() {
            return () -> cooldownAllowed;
        }

        private DropSink dropSink() {
            return (position, numericId) -> dropped.add(position);
        }

        private ExecutionOrigin executionOrigin() {
            return () -> origin;
        }

        private MechanicConfig mechanicConfig() {
            return new MechanicConfig() {
                @Override
                public Optional<String> getString(String key) {
                    return config.containsKey(key) ? Optional.of(config.get(key).toString()) : Optional.empty();
                }

                @Override
                public Optional<Integer> getInt(String key) {
                    return config.containsKey(key) && config.get(key) instanceof Number n
                            ? Optional.of(n.intValue()) : Optional.empty();
                }

                @Override
                public Optional<Boolean> getBoolean(String key) {
                    return config.containsKey(key) && config.get(key) instanceof Boolean b
                            ? Optional.of(b) : Optional.empty();
                }

                @Override
                public Optional<Double> getDouble(String key) {
                    return config.containsKey(key) && config.get(key) instanceof Number n
                            ? Optional.of(n.doubleValue()) : Optional.empty();
                }
            };
        }
    }
}