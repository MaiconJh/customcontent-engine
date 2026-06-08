package com.customcontentengine.bootstrap;

import com.customcontentengine.adapter.bukkit.AreaBreakDebugCommandAdapter;
import com.customcontentengine.adapter.bukkit.BlockBreakAdapter;
import com.customcontentengine.adapter.bukkit.BlockPlaceAdapter;
import com.customcontentengine.adapter.bukkit.BukkitDropAdapter;
import com.customcontentengine.adapter.bukkit.BukkitItemMetadataAdapter;
import com.customcontentengine.adapter.bukkit.BukkitMiningVisualAdapter;
import com.customcontentengine.adapter.bukkit.BukkitWorldMutationAdapter;
import com.customcontentengine.adapter.bukkit.ItemCommandAdapter;
import com.customcontentengine.adapter.bukkit.MiningInputAdapter;
import com.customcontentengine.adapter.bukkit.MiningProcessingDriver;
import com.customcontentengine.adapter.platform.PaperRegionSafetyAdapter;
import com.customcontentengine.adapter.platform.PaperSchedulerAdapter;
import com.customcontentengine.adapter.persistence.PdcBlockCodec;
import com.customcontentengine.adapter.persistence.PdcBlockStore;
import com.customcontentengine.adapter.yaml.YamlDefinitionLoader;
import com.customcontentengine.adapter.yaml.YamlDefinitionValidator;
import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.application.item.ItemService;
import com.customcontentengine.application.mechanic.AreaBreakEventTriggerService;
import com.customcontentengine.application.mechanic.AreaBreakRuntimeService;
import com.customcontentengine.application.mechanic.MechanicBindingValidator;
import com.customcontentengine.application.mechanic.MechanicRegistry;
import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.application.mining.InMemoryMiningSessionRepository;
import com.customcontentengine.application.mining.CustomMiningCompletionService;
import com.customcontentengine.application.mining.MiningRuntimeProcessor;
import com.customcontentengine.application.mining.MiningSessionService;
import com.customcontentengine.builtin.mechanic.AreaBreakMechanic;
import com.customcontentengine.domain.mining.MiningDurationPolicy;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import java.util.List;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomContentPlugin extends JavaPlugin {
    private static final int MINING_MAX_SESSIONS_PER_RUN = 64;
    private static final long MINING_PROCESSING_PERIOD_TICKS = 2L;

    private MiningProcessingDriver miningProcessingDriver;

    @Override
    public void onEnable() {
        saveDefaultConfigResource("definitions.yml");

        DefinitionRegistry registry = new YamlDefinitionLoader(new YamlDefinitionValidator())
                .load(getDataFolder().toPath().resolve("definitions.yml").toFile());
        PdcBlockStore blockStore = new PdcBlockStore(this, new PdcBlockCodec());
        BukkitItemMetadataAdapter itemMetadata = new BukkitItemMetadataAdapter(this);
        ItemService<ItemStack> itemService = new ItemService<>(registry, itemMetadata);
        BukkitDropAdapter dropPort = new BukkitDropAdapter(itemService);
        BlockService blockService = new BlockService(registry, blockStore, dropPort);
        MechanicRegistry mechanicRegistry = new MechanicRegistry(List.of(new AreaBreakMechanic()));
        new MechanicBindingValidator(mechanicRegistry, java.util.Set.of(AreaBreakMechanic.ID))
                .validate(registry.mechanicBindings());
        PaperRegionSafetyAdapter regionSafety = new PaperRegionSafetyAdapter();
        BukkitWorldMutationAdapter worldMutation = new BukkitWorldMutationAdapter(regionSafety);
        InMemoryCooldowns cooldowns = new InMemoryCooldowns();
        PaperSchedulerAdapter scheduler = new PaperSchedulerAdapter(this);
        AreaBreakRuntimeService areaBreakRuntime = new AreaBreakRuntimeService(
                mechanicRegistry,
                AreaBreakMechanic.ID,
                registry,
                blockStore,
                dropPort,
                worldMutation,
                cooldowns,
                scheduler,
                regionSafety);
        AreaBreakEventTriggerService areaBreakEventTrigger = new AreaBreakEventTriggerService(
                registry.mechanicBindings(),
                AreaBreakMechanic.ID,
                areaBreakRuntime);
        MiningSessionService miningSessionService = new MiningSessionService(
                new InMemoryMiningSessionRepository(),
                MiningDurationPolicy.DEFAULT);
        BukkitMiningVisualAdapter miningVisual = new BukkitMiningVisualAdapter(this);
        CustomMiningCompletionService miningCompletion = new CustomMiningCompletionService(
                registry,
                blockStore,
                worldMutation,
                dropPort,
                regionSafety,
                areaBreakEventTrigger);
        MiningRuntimeProcessor miningRuntimeProcessor = new MiningRuntimeProcessor(
                miningSessionService,
                miningVisual,
                miningCompletion,
                scheduler);

        getServer().getPluginManager().registerEvents(new BlockPlaceAdapter(blockService, itemMetadata), this);
        getServer().getPluginManager().registerEvents(
                new BlockBreakAdapter(blockService, itemMetadata, areaBreakEventTrigger),
                this);
        getServer().getPluginManager().registerEvents(
                new MiningInputAdapter(registry, blockStore, itemMetadata, miningSessionService, miningVisual),
                this);
        miningProcessingDriver = new MiningProcessingDriver(
                this,
                miningRuntimeProcessor,
                MINING_MAX_SESSIONS_PER_RUN,
                MINING_PROCESSING_PERIOD_TICKS);
        miningProcessingDriver.start();

        PluginCommand command = getCommand("givecustomitem");
        if (command != null) {
            command.setExecutor(new ItemCommandAdapter(itemService));
        }

        PluginCommand areaBreakDebugCommand = getCommand("debugareabreak");
        if (areaBreakDebugCommand != null) {
            areaBreakDebugCommand.setExecutor(new AreaBreakDebugCommandAdapter(areaBreakRuntime));
        }
    }

    @Override
    public void onDisable() {
        if (miningProcessingDriver != null) {
            miningProcessingDriver.stop();
            miningProcessingDriver = null;
        }
    }

    private void saveDefaultConfigResource(String resourcePath) {
        if (!getDataFolder().toPath().resolve(resourcePath).toFile().exists()) {
            saveResource(resourcePath, false);
        }
    }
}
