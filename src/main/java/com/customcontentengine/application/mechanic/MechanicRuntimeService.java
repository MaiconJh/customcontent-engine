package com.customcontentengine.application.mechanic;

import com.customcontentengine.application.mechanic.capability.DefinitionDropSink;
import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.application.mechanic.capability.MappedMechanicConfig;
import com.customcontentengine.application.mechanic.capability.SimpleBudgetView;
import com.customcontentengine.application.mechanic.capability.StaticExecutionOrigin;
import com.customcontentengine.application.mechanic.capability.StoredBlockMutation;
import com.customcontentengine.application.mechanic.capability.StoredBlockPlacement;
import com.customcontentengine.application.mechanic.capability.StoredBlockQuery;
import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockPlacement;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.internalapi.mechanic.capability.MechanicConfig;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Map;
import java.util.Objects;

public final class MechanicRuntimeService {
    private static final int DEFAULT_BUDGET = 1;
    private static final long DEFAULT_COOLDOWN_MILLIS = 100L;

    private final MechanicRegistry mechanicRegistry;
    private final DefinitionRegistry definitions;
    private final BlockStorePort blockStore;
    private final DropPort dropPort;
    private final WorldMutationPort worldMutation;
    private final InMemoryCooldowns cooldowns;
    private final SchedulerPort schedulerPort;
    private final RegionSafetyPort regionSafety;

    public MechanicRuntimeService(
            MechanicRegistry mechanicRegistry,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort,
            RegionSafetyPort regionSafety) {
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "mechanicRegistry");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
    }

    public MechanicResult execute(MechanicBinding binding, WorldPosition origin, String actorKey) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");

        return new MechanicExecutor(
                mechanicRegistry,
                contextFactory(binding, origin, actorKey, true),
                schedulerPort,
                anchor -> contextFactory(binding, anchor, actorKey, false),
                8)
                .execute(binding.mechanicId());
    }

    private MechanicContextFactory contextFactory(MechanicBinding binding, WorldPosition origin, String actorKey, boolean initialExecution) {
        return new MechanicContextFactory(Map.of(
                BlockQuery.class, new StoredBlockQuery(blockStore),
                BlockMutation.class, new StoredBlockMutation(blockStore, worldMutation, regionSafety::canAccess),
                BlockPlacement.class, new StoredBlockPlacement(blockStore, worldMutation, regionSafety::canAccess),
                BudgetView.class, new SimpleBudgetView(DEFAULT_BUDGET),
                CooldownView.class, cooldownView(actorKey, binding.mechanicId(), initialExecution),
                DropSink.class, new DefinitionDropSink(definitions, dropPort),
                ExecutionOrigin.class, new StaticExecutionOrigin(origin),
                MechanicConfig.class, new MappedMechanicConfig(binding.arguments())
        ));
    }

    private CooldownView cooldownView(String actorKey, MechanicId mechanicId, boolean initialExecution) {
        if (initialExecution) {
            return cooldowns.view(actorKey + ":" + mechanicId.value(), DEFAULT_COOLDOWN_MILLIS);
        }
        return () -> true;
    }
}