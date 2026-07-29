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
import com.customcontentengine.internalapi.mechanic.capability.MechanicArguments;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.ProtectionPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MechanicRuntimeService {
    private static final int DEFAULT_BUDGET = 64;
    private static final long DEFAULT_COOLDOWN_MILLIS = 100L;

    private final MechanicRegistry mechanicRegistry;
    private final DefinitionRegistry definitions;
    private final BlockStorePort blockStore;
    private final DropPort dropPort;
    private final WorldMutationPort worldMutation;
    private final InMemoryCooldowns cooldowns;
    private final SchedulerPort schedulerPort;
    private final RegionSafetyPort regionSafety;
    private final ProtectionPort protectionPort;

    public MechanicRuntimeService(
            MechanicRegistry mechanicRegistry,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort,
            RegionSafetyPort regionSafety,
            ProtectionPort protectionPort) {
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "mechanicRegistry");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
        this.protectionPort = protectionPort;
    }

    public MechanicRuntimeService(
            MechanicRegistry mechanicRegistry,
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            InMemoryCooldowns cooldowns,
            SchedulerPort schedulerPort,
            RegionSafetyPort regionSafety) {
        this(mechanicRegistry, definitions, blockStore, dropPort, worldMutation, cooldowns, schedulerPort, regionSafety, null);
    }

    public MechanicResult execute(MechanicBinding binding, WorldPosition origin, String actorKey,
                                  Map<String, Object> extraArguments) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actorKey, "actorKey");

        return new MechanicExecutor(
                mechanicRegistry,
                contextFactory(binding, origin, actorKey, true, extraArguments),
                schedulerPort,
                anchor -> contextFactory(binding, anchor, actorKey, false, extraArguments),
                8)
                .execute(binding.mechanicId());
    }

    private MechanicContextFactory contextFactory(MechanicBinding binding, WorldPosition origin, String actorKey, boolean initialExecution,
                                                  Map<String, Object> extraArguments) {
        Map<String, Object> combinedArgs = new LinkedHashMap<>(binding.arguments());
        if (extraArguments != null) {
            combinedArgs.putAll(extraArguments);
        }
        Map<Class<?>, Object> capabilities = new LinkedHashMap<>();
        capabilities.put(BlockQuery.class, protectedBlockQuery(actorKey));
        capabilities.put(BlockMutation.class, new StoredBlockMutation(blockStore, worldMutation, regionSafety::canAccess));
        capabilities.put(BlockPlacement.class, new StoredBlockPlacement(blockStore, worldMutation, regionSafety::canAccess));
        capabilities.put(BudgetView.class, new SimpleBudgetView(DEFAULT_BUDGET));
        capabilities.put(CooldownView.class, cooldownView(actorKey, binding.mechanicId(), initialExecution));
        capabilities.put(DropSink.class, new DefinitionDropSink(definitions, dropPort));
        capabilities.put(ExecutionOrigin.class, new StaticExecutionOrigin(origin));
        capabilities.put(MechanicConfig.class, new MappedMechanicConfig(combinedArgs));
        if (!combinedArgs.isEmpty()) {
            capabilities.put(MechanicArguments.class, new MapBackedMechanicArguments(combinedArgs));
        }
        return new MechanicContextFactory(capabilities);
    }

    private BlockQuery protectedBlockQuery(String actorKey) {
        BlockQuery delegate = new StoredBlockQuery(blockStore);
        return position -> {
            boolean canBuild = protectionPort == null || protectionPort.canBuild(actorKey, position);
            Optional<Short> result = canBuild ? delegate.findCustomBlockNumericId(position) : Optional.empty();
            System.out.println("[DEBUG] MechanicRuntimeService protectedBlockQuery pos=" + position + " actor=" + actorKey + " canBuild=" + canBuild + " result=" + result);
            return result;
        };
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

    private CooldownView cooldownView(String actorKey, MechanicId mechanicId, boolean initialExecution) {
        if (initialExecution) {
            return cooldowns.view(actorKey + ":" + mechanicId.value(), DEFAULT_COOLDOWN_MILLIS);
        }
        return () -> true;
    }
}