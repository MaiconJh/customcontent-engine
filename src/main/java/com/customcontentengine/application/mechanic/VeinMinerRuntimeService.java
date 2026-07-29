package com.customcontentengine.application.mechanic;

import com.customcontentengine.application.budget.WorkBudget;
import com.customcontentengine.application.mechanic.capability.DefinitionDropSink;
import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.application.mechanic.capability.StaticExecutionOrigin;
import com.customcontentengine.application.mechanic.capability.StoredBlockMutation;
import com.customcontentengine.application.mechanic.capability.StoredBlockQuery;
import com.customcontentengine.application.mechanic.capability.WorkBudgetView;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.ActorState;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.internalapi.mechanic.capability.MechanicArguments;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.ProtectionPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.ToolWearPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Runtime service that executes the {@code vein_miner} official mechanic.
 *
 * <p>Follows the same Folia-safe pattern as {@link AreaBreakRuntimeService}:
 * synchronous processing with {@link WorkBudget} and {@link MechanicResult.Partial}
 * rescheduling. The {@link EnchantmentView} and {@link ActorState} capabilities
 * (when provided) let the mechanic apply Fortune/Silk Touch and react to the
 * actor's sneaking state without depending on Bukkit types.</p>
 *
 * <p>After execution, per-block tool wear is applied via {@link ToolWearPort}
 * when {@code durability_per_block} is enabled, and block queries are hidden
 * behind {@link ProtectionPort} so protected blocks are skipped without being
 * counted or mutated.</p>
 */
public final class VeinMinerRuntimeService {
    private static final int VEIN_MINER_BUDGET = 64;
    private static final long COOLDOWN_MILLIS = 500L;
    private static final Logger LOGGER = Logger.getLogger(VeinMinerRuntimeService.class.getName());

    private final MechanicRegistry mechanicRegistry;
    private final MechanicId mechanicId;
    private final DefinitionRegistry definitions;
    private final BlockStorePort blockStore;
    private final DropPort dropPort;
    private final WorldMutationPort worldMutation;
    private final InMemoryCooldowns cooldowns;
    private final SchedulerPort schedulerPort;
    private final RegionSafetyPort regionSafety;
    private final ToolWearPort toolWearPort;
    private final ProtectionPort protectionPort;

    public VeinMinerRuntimeService(
            MechanicRegistry mechanicRegistry,
            MechanicId mechanicId,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort,
            RegionSafetyPort regionSafety,
            ToolWearPort toolWearPort,
            ProtectionPort protectionPort) {
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "mechanicRegistry");
        this.mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
        this.toolWearPort = toolWearPort;
        this.protectionPort = protectionPort;
    }

    public MechanicResult execute(
            WorldPosition origin,
            String actorKey,
            CustomItemId toolId,
            EnchantmentView enchantmentView,
            ActorState actorState,
            Map<String, Object> arguments) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");
        if (actorKey.isBlank()) {
            throw new IllegalArgumentException("actorKey must not be blank");
        }

        if (isAllAdjacent(arguments)) {
            LOGGER.info(() -> "vein_miner using ALL_ADJACENT shape: applying conservative limits "
                    + "(max_blocks <= 32, max_depth <= 10) to protect TPS.");
        }

        RegionSafetyTracker safetyTracker = new RegionSafetyTracker(regionSafety);
        MechanicContextFactory contextFactory = contextFactory(
                origin, actorKey, true, safetyTracker, enchantmentView, actorState, arguments);
        MechanicResult result = new MechanicExecutor(
                mechanicRegistry,
                contextFactory,
                schedulerPort,
                anchor -> contextFactory(
                        anchor, actorKey, false, new RegionSafetyTracker(regionSafety),
                        enchantmentView, actorState, arguments),
                8)
                .execute(mechanicId);
        MechanicResult applied = safetyTracker.apply(result);
        applyDurability(applied, actorKey, toolId, arguments);
        return applied;
    }

    private void applyDurability(
            MechanicResult result,
            String actorKey,
            CustomItemId toolId,
            Map<String, Object> arguments) {
        if (toolWearPort == null || toolId == null) {
            return;
        }
        int affected;
        if (result instanceof MechanicResult.Done done) {
            affected = done.affectedBlocks();
        } else if (result instanceof MechanicResult.Partial partial) {
            affected = partial.affectedBlocks();
        } else {
            return;
        }
        if (affected <= 0) {
            return;
        }
        boolean perBlock = durabilityPerBlock(arguments);
        int count = perBlock ? affected : 1;
        toolWearPort.applyWearIfNeeded(actorKey, toolId, count);
    }

    private boolean durabilityPerBlock(Map<String, Object> arguments) {
        if (arguments == null) {
            return true;
        }
        Object value = arguments.get("durability_per_block");
        return value == null || Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isAllAdjacent(Map<String, Object> arguments) {
        if (arguments == null) {
            return false;
        }
        Object value = arguments.get("shape");
        return value != null && "ALL_ADJACENT".equalsIgnoreCase(String.valueOf(value));
    }

    private MechanicContextFactory contextFactory(
            WorldPosition origin,
            String actorKey,
            boolean initialExecution,
            RegionSafetyTracker safetyTracker,
            EnchantmentView enchantmentView,
            ActorState actorState,
            Map<String, Object> arguments) {
        Map<Class<?>, Object> capabilities = new LinkedHashMap<>();
        capabilities.put(BlockQuery.class, blockQuery(safetyTracker, actorKey));
        capabilities.put(BlockMutation.class, blockMutation(safetyTracker));
        capabilities.put(BudgetView.class, new WorkBudgetView(new WorkBudget(VEIN_MINER_BUDGET)));
        capabilities.put(CooldownView.class, cooldownView(actorKey, initialExecution));
        capabilities.put(DropSink.class, dropSink(safetyTracker));
        capabilities.put(ExecutionOrigin.class, new StaticExecutionOrigin(origin));
        if (enchantmentView != null) {
            capabilities.put(EnchantmentView.class, enchantmentView);
        }
        if (actorState != null) {
            capabilities.put(ActorState.class, actorState);
        }
        if (arguments != null && !arguments.isEmpty()) {
            capabilities.put(MechanicArguments.class, new MapBackedMechanicArguments(arguments));
        }
        return new MechanicContextFactory(capabilities);
    }

    private static final class MapBackedMechanicArguments implements MechanicArguments {
        private final Map<String, Object> arguments;

        private MapBackedMechanicArguments(Map<String, Object> arguments) {
            this.arguments = Map.copyOf(arguments);
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

    private BlockQuery blockQuery(RegionSafetyTracker safetyTracker, String actorKey) {
        BlockQuery delegate = new StoredBlockQuery(blockStore);
        return position -> {
            boolean safe = safetyTracker.canAccess(position);
            boolean canBuild = protectionPort == null || protectionPort.canBuild(actorKey, position);
            Optional<Short> result = (safe && canBuild) ? delegate.findCustomBlockNumericId(position) : Optional.empty();
            System.out.println("[DEBUG] VeinMinerRuntimeService blockQuery pos=" + position + " safe=" + safe + " canBuild=" + canBuild + " result=" + result);
            return result;
        };
    }

    private BlockMutation blockMutation(RegionSafetyTracker safetyTracker) {
        BlockMutation delegate = new StoredBlockMutation(blockStore, worldMutation, safetyTracker::canAccess);
        return position -> {
            if (safetyTracker.canAccess(position)) {
                delegate.breakBlock(position);
            }
        };
    }

    private DropSink dropSink(RegionSafetyTracker safetyTracker) {
        DropSink delegate = new DefinitionDropSink(definitions, dropPort);
        return (position, numericId) -> {
            if (safetyTracker.canAccess(position)) {
                delegate.dropFor(position, numericId);
            }
        };
    }

    private CooldownView cooldownView(String actorKey, boolean initialExecution) {
        if (initialExecution) {
            return cooldowns.view(actorKey + ":" + mechanicId.value(), COOLDOWN_MILLIS);
        }
        return () -> true;
    }

    private static final class RegionSafetyTracker {
        private final RegionSafetyPort regionSafety;
        private final LinkedHashSet<WorldPosition> unsafePositions = new LinkedHashSet<>();

        private RegionSafetyTracker(RegionSafetyPort regionSafety) {
            this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
        }

        private boolean canAccess(WorldPosition position) {
            if (regionSafety.canAccess(position)) {
                return true;
            }
            unsafePositions.add(position);
            return false;
        }

        private MechanicResult apply(MechanicResult result) {
            if (unsafePositions.isEmpty() || result instanceof MechanicResult.Rejected) {
                return result;
            }
            List<WorldPosition> remaining = new ArrayList<>(unsafePositions);
            if (result instanceof MechanicResult.Partial partial) {
                remaining.addAll(partial.remaining());
                return new MechanicResult.Partial(partial.affectedBlocks(), remaining);
            }
            if (result instanceof MechanicResult.Done done) {
                return new MechanicResult.Partial(done.affectedBlocks(), remaining);
            }
            return result;
        }
    }
}
