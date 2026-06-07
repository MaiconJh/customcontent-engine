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
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
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

    public AreaBreakRuntimeService(
            MechanicRegistry mechanicRegistry,
            MechanicId mechanicId,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort) {
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "mechanicRegistry");
        this.mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort");
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

        MechanicContextFactory contextFactory = contextFactory(origin, actorKey, true, excluded);
        return new MechanicExecutor(
                mechanicRegistry,
                contextFactory,
                schedulerPort,
                anchor -> contextFactory(anchor, actorKey, false, excluded),
                8)
                .execute(mechanicId);
    }

    private MechanicContextFactory contextFactory(
            WorldPosition origin,
            String actorKey,
            boolean initialExecution,
            Set<WorldPosition> excludedPositions) {
        return new MechanicContextFactory(Map.of(
                BlockQuery.class, blockQuery(excludedPositions),
                BlockMutation.class, blockMutation(excludedPositions),
                BudgetView.class, new WorkBudgetView(new WorkBudget(AREA_BREAK_BUDGET)),
                CooldownView.class, cooldownView(actorKey, initialExecution),
                DropSink.class, dropSink(excludedPositions),
                ExecutionOrigin.class, new StaticExecutionOrigin(origin)
        ));
    }

    private BlockQuery blockQuery(Set<WorldPosition> excludedPositions) {
        BlockQuery delegate = new StoredBlockQuery(blockStore);
        return position -> {
            if (excludedPositions.contains(position)) {
                return Optional.empty();
            }
            return delegate.findCustomBlockNumericId(position);
        };
    }

    private BlockMutation blockMutation(Set<WorldPosition> excludedPositions) {
        BlockMutation delegate = new StoredBlockMutation(blockStore, worldMutation);
        return position -> {
            if (!excludedPositions.contains(position)) {
                delegate.breakBlock(position);
            }
        };
    }

    private DropSink dropSink(Set<WorldPosition> excludedPositions) {
        DropSink delegate = new DefinitionDropSink(definitions, dropPort);
        return (position, numericId) -> {
            if (!excludedPositions.contains(position)) {
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
}
