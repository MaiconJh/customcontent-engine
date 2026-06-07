package com.customcontentengine.application.mechanic;

import com.customcontentengine.application.budget.WorkBudget;
import com.customcontentengine.application.mechanic.capability.DefinitionDropSink;
import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.application.mechanic.capability.StaticExecutionOrigin;
import com.customcontentengine.application.mechanic.capability.StoredBlockMutation;
import com.customcontentengine.application.mechanic.capability.StoredBlockQuery;
import com.customcontentengine.application.mechanic.capability.WorkBudgetView;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AreaBreakRuntimeService {
    private static final int AREA_BREAK_BUDGET = 32;
    private static final long COOLDOWN_MILLIS = 500L;

    private final MechanicRegistry mechanicRegistry;
    private final MechanicId mechanicId;
    private final DefinitionRegistry definitions;
    private final BlockStorePort blockStore;
    private final DropPort dropPort;
    private final WorldMutationPort worldMutation;
    private final InMemoryCooldowns cooldowns;
    private final SchedulerPort schedulerPort;
    private final RegionSafetyPort regionSafety;

    public AreaBreakRuntimeService(
            MechanicRegistry mechanicRegistry,
            MechanicId mechanicId,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort) {
        this(
                mechanicRegistry,
                mechanicId,
                definitions,
                blockStore,
                dropPort,
                worldMutation,
                cooldowns,
                schedulerPort,
                position -> true);
    }

    public AreaBreakRuntimeService(
            MechanicRegistry mechanicRegistry,
            MechanicId mechanicId,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort,
            RegionSafetyPort regionSafety) {
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "mechanicRegistry");
        this.mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
    }

    public MechanicResult execute(WorldPosition origin, String actorKey) {
        return execute(origin, actorKey, Set.of());
    }

    public MechanicResult executeAdditionalArea(WorldPosition origin, String actorKey) {
        return execute(origin, actorKey, Set.of(origin));
    }

    private MechanicResult execute(WorldPosition origin, String actorKey, Set<WorldPosition> excludedPositions) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");
        Set<WorldPosition> excluded = Set.copyOf(Objects.requireNonNull(excludedPositions, "excludedPositions"));
        if (actorKey.isBlank()) {
            throw new IllegalArgumentException("actorKey must not be blank");
        }

        RegionSafetyTracker safetyTracker = new RegionSafetyTracker(regionSafety);
        MechanicContextFactory contextFactory = contextFactory(origin, actorKey, true, excluded, safetyTracker);
        MechanicResult result = new MechanicExecutor(
                mechanicRegistry,
                contextFactory,
                schedulerPort,
                anchor -> contextFactory(anchor, actorKey, false, excluded, new RegionSafetyTracker(regionSafety)),
                8)
                .execute(mechanicId);
        return safetyTracker.apply(result);
    }

    private MechanicContextFactory contextFactory(
            WorldPosition origin,
            String actorKey,
            boolean initialExecution,
            Set<WorldPosition> excludedPositions,
            RegionSafetyTracker safetyTracker) {
        return new MechanicContextFactory(Map.of(
                BlockQuery.class, blockQuery(excludedPositions, safetyTracker),
                BlockMutation.class, blockMutation(excludedPositions, safetyTracker),
                BudgetView.class, new WorkBudgetView(new WorkBudget(AREA_BREAK_BUDGET)),
                CooldownView.class, cooldownView(actorKey, initialExecution),
                DropSink.class, dropSink(excludedPositions, safetyTracker),
                ExecutionOrigin.class, new StaticExecutionOrigin(origin)
        ));
    }

    private BlockQuery blockQuery(Set<WorldPosition> excludedPositions, RegionSafetyTracker safetyTracker) {
        BlockQuery delegate = new StoredBlockQuery(blockStore);
        return position -> {
            if (excludedPositions.contains(position)) {
                return Optional.empty();
            }
            if (!safetyTracker.canAccess(position)) {
                return Optional.empty();
            }
            return delegate.findCustomBlockNumericId(position);
        };
    }

    private BlockMutation blockMutation(Set<WorldPosition> excludedPositions, RegionSafetyTracker safetyTracker) {
        BlockMutation delegate = new StoredBlockMutation(blockStore, worldMutation, safetyTracker::canAccess);
        return position -> {
            if (!excludedPositions.contains(position) && safetyTracker.canAccess(position)) {
                delegate.breakBlock(position);
            }
        };
    }

    private DropSink dropSink(Set<WorldPosition> excludedPositions, RegionSafetyTracker safetyTracker) {
        DropSink delegate = new DefinitionDropSink(definitions, dropPort);
        return (position, numericId) -> {
            if (!excludedPositions.contains(position) && safetyTracker.canAccess(position)) {
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
