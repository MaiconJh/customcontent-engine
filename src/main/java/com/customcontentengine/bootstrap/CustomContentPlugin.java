package com.customcontentengine.bootstrap;

import com.customcontentengine.adapter.bukkit.BlockBreakAdapter;
import com.customcontentengine.adapter.bukkit.BlockPlaceAdapter;
import com.customcontentengine.adapter.bukkit.ItemCommandAdapter;
import com.customcontentengine.adapter.persistence.PdcBlockCodec;
import com.customcontentengine.adapter.persistence.PdcBlockStore;
import com.customcontentengine.adapter.platform.PaperSchedulerAdapter;
import com.customcontentengine.adapter.yaml.YamlDefinitionLoader;
import com.customcontentengine.adapter.yaml.YamlDefinitionValidator;
import com.customcontentengine.application.block.BlockService;
import com.customcontentengine.application.item.ItemService;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.WorldMutationPort;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomContentPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfigResource("definitions.yml");

        DefinitionRegistry registry = new YamlDefinitionLoader(new YamlDefinitionValidator())
                .load(getDataFolder().toPath().resolve("definitions.yml").toFile());
        PaperSchedulerAdapter scheduler = new PaperSchedulerAdapter(this);
        PdcBlockStore blockStore = new PdcBlockStore(this, new PdcBlockCodec());
        WorldMutationPort worldMutation = (position, materialBase) -> { };
        DropPort dropPort = (position, drops) -> { };
        BlockService blockService = new BlockService(registry, scheduler, blockStore, worldMutation, dropPort);
        ItemService itemService = new ItemService(registry);

        getServer().getPluginManager().registerEvents(new BlockBreakAdapter(blockService), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceAdapter(blockService), this);

        PluginCommand command = getCommand("givecustomitem");
        if (command != null) {
            command.setExecutor(new ItemCommandAdapter(itemService));
        }
    }

    private void saveDefaultConfigResource(String resourcePath) {
        if (!getDataFolder().toPath().resolve(resourcePath).toFile().exists()) {
            saveResource(resourcePath, false);
        }
    }
}
