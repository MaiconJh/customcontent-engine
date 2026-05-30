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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlDefinitionLoader {
    private final YamlDefinitionValidator validator;

    public YamlDefinitionLoader(YamlDefinitionValidator validator) {
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
    }

    public DefinitionRegistry load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        validator.validate(yaml);
        return new DefinitionRegistry(loadBlocks(yaml.getConfigurationSection("blocks")), loadItems(yaml.getConfigurationSection("items")));
    }

    private List<BlockDef> loadBlocks(ConfigurationSection blocksSection) {
        List<BlockDef> blocks = new ArrayList<>();
        for (String id : blocksSection.getKeys(false)) {
            ConfigurationSection section = blocksSection.getConfigurationSection(id);
            List<DropTable.Entry> drops = new ArrayList<>();
            for (java.util.Map<?, ?> drop : section.getMapList("drops")) {
                Object amount = drop.get("amount");
                drops.add(new DropTable.Entry(String.valueOf(drop.get("item")), amount instanceof Number number ? number.intValue() : 1));
            }
            blocks.add(new BlockDef(
                    new CustomBlockId(id),
                    (short) section.getInt("numeric_id"),
                    section.getString("material_base"),
                    section.getInt("custom_model_data"),
                    section.getString("required_tool"),
                    new DropTable(drops)
            ));
        }
        return blocks;
    }

    private List<ItemDef> loadItems(ConfigurationSection itemsSection) {
        List<ItemDef> items = new ArrayList<>();
        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            ConfigurationSection attributes = section.getConfigurationSection("attributes");
            items.add(new ItemDef(
                    new CustomItemId(id),
                    section.getString("material_base"),
                    section.getInt("custom_model_data"),
                    new ToolAttributes(
                            attributes == null ? 0.0 : attributes.getDouble("damage"),
                            attributes == null ? 0.0 : attributes.getDouble("speed"),
                            attributes == null ? 0 : attributes.getInt("durability")
                    )
            ));
        }
        return items;
    }
}
