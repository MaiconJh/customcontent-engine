package com.customcontentengine.adapter.yaml;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlDefinitionLoader {
    private final YamlDefinitionValidator validator;

    public YamlDefinitionLoader(YamlDefinitionValidator validator) {
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
    }

    public DefinitionRegistry load(File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            validator.validateRoot(yaml);
            DefinitionRegistry registry = new DefinitionRegistry(
                    loadBlocks(yaml.getConfigurationSection("blocks")),
                    loadItems(yaml.getConfigurationSection("items"))
            );
            validator.validateCrossReferences(registry);
            return registry;
        } catch (YamlDefinitionException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new YamlDefinitionException("Invalid definitions.yml: " + exception.getMessage());
        }
    }

    private List<BlockDef> loadBlocks(ConfigurationSection blocksSection) {
        List<BlockDef> blocks = new ArrayList<>();
        for (String id : blocksSection.getKeys(false)) {
            ConfigurationSection section = blocksSection.getConfigurationSection(id);
            validator.validateBlockSection(id, section);
            blocks.add(new BlockDef(
                    blockId(id),
                    (short) section.getInt("numeric_id"),
                    section.getString("material_base"),
                    section.getInt("custom_model_data"),
                    section.getString("required_tool"),
                    new DropTable(loadDrops(id, section.getMapList("drops")))
            ));
        }
        return blocks;
    }

    private List<DropTable.Entry> loadDrops(String blockId, List<Map<?, ?>> dropMaps) {
        List<DropTable.Entry> drops = new ArrayList<>();
        for (int index = 0; index < dropMaps.size(); index++) {
            Map<?, ?> drop = dropMaps.get(index);
            Object item = drop.get("item");
            Object amount = drop.get("amount");
            validator.validateDrop(blockId, index, item, amount);
            drops.add(new DropTable.Entry((String) item, ((Number) amount).intValue()));
        }
        return drops;
    }

    private List<ItemDef> loadItems(ConfigurationSection itemsSection) {
        List<ItemDef> items = new ArrayList<>();
        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            validator.validateItemSection(id, section);
            ConfigurationSection attributes = section.getConfigurationSection("attributes");
            items.add(new ItemDef(
                    itemId(id),
                    section.getString("material_base"),
                    section.getInt("custom_model_data"),
                    new ToolAttributes(
                            attributes.getDouble("damage"),
                            attributes.getDouble("speed"),
                            attributes.getInt("durability")
                    )
            ));
        }
        return items;
    }

    private CustomBlockId blockId(String id) {
        try {
            return new CustomBlockId(id);
        } catch (IllegalArgumentException exception) {
            throw new YamlDefinitionException("Invalid definitions.yml: blocks." + id + " has invalid id: " + exception.getMessage());
        }
    }

    private CustomItemId itemId(String id) {
        try {
            return new CustomItemId(id);
        } catch (IllegalArgumentException exception) {
            throw new YamlDefinitionException("Invalid definitions.yml: items." + id + " has invalid id: " + exception.getMessage());
        }
    }
}
