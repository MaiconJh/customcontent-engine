package com.customcontentengine.adapter.yaml;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.durability.ToolBreakPolicy;
import com.customcontentengine.domain.durability.ToolDurabilityDefinition;
import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.mining.BlockTierRequirement;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.domain.mining.ToolTier;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
            DefinitionRegistry registry = new DefinitionRegistry(
                    loadBlocks(yaml.getConfigurationSection("blocks")),
                    loadItems(itemsSection),
                    loadMechanicBindings(itemsSection)
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
                    new DropTable(loadDrops(id, section.getMapList("drops"))),
                    loadMiningHardness(section),
                    loadMiningRequiredTier(section)
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
                    ),
                    loadMiningSpeed(section),
                    loadMiningToolTier(section),
                    loadDurability(section)
            ));
        }
        return items;
    }

    private Optional<MiningHardness> loadMiningHardness(ConfigurationSection section) {
        if (!section.contains("mining")) {
            return Optional.empty();
        }
        return Optional.of(new MiningHardness(section.getConfigurationSection("mining").getDouble("hardness")));
    }

    private Optional<MiningSpeed> loadMiningSpeed(ConfigurationSection section) {
        if (!section.contains("mining")) {
            return Optional.empty();
        }
        return Optional.of(new MiningSpeed(section.getConfigurationSection("mining").getDouble("speed")));
    }

    private Optional<ToolTier> loadMiningToolTier(ConfigurationSection section) {
        if (!section.contains("mining")) {
            return Optional.empty();
        }
        ConfigurationSection mining = section.getConfigurationSection("mining");
        if (mining == null || !mining.contains("tier")) {
            return Optional.empty();
        }
        return Optional.of(new ToolTier(mining.getInt("tier")));
    }

    private Optional<BlockTierRequirement> loadMiningRequiredTier(ConfigurationSection section) {
        if (!section.contains("mining")) {
            return Optional.empty();
        }
        ConfigurationSection mining = section.getConfigurationSection("mining");
        if (mining == null || !mining.contains("required_tier")) {
            return Optional.empty();
        }
        return Optional.of(new BlockTierRequirement(mining.getInt("required_tier")));
    }

    private Optional<ToolDurabilityDefinition> loadDurability(ConfigurationSection section) {
        if (!section.contains("durability")) {
            return Optional.empty();
        }
        ConfigurationSection durability = section.getConfigurationSection("durability");
        int max = durability.getInt("max");
        int damage = durability.getInt("damage_on_custom_block_break", 0);
        boolean breakWhenZero = durability.getBoolean("break_when_zero", true);
        return Optional.of(new ToolDurabilityDefinition(
                max,
                damage,
                breakWhenZero ? ToolBreakPolicy.BREAK : ToolBreakPolicy.PRESERVE));
    }

    private MechanicBindingRegistry loadMechanicBindings(ConfigurationSection itemsSection) {
        List<MechanicBinding> bindings = new ArrayList<>();
        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            if (section == null || !section.contains("mechanics")) {
                continue;
            }
            ConfigurationSection mechanics = section.getConfigurationSection("mechanics");
            CustomItemId itemId = itemId(id);
            for (String triggerKey : mechanics.getKeys(false)) {
                MechanicTrigger trigger = mechanicTrigger(id, triggerKey);
                List<?> mechanicIds = mechanics.getList(triggerKey);
                for (int index = 0; index < mechanicIds.size(); index++) {
                    Object raw = mechanicIds.get(index);
                    if (raw instanceof String idValue) {
                        bindings.add(new MechanicBinding(
                                itemId,
                                trigger,
                                mechanicId(id, triggerKey, index, idValue)));
                    } else if (raw instanceof Map<?, ?> entry) {
                        Object rawId = entry.get("id");
                        if (!(rawId instanceof String idValue)) {
                            throw new YamlDefinitionException("Invalid definitions.yml: items." + id
                                    + ".mechanics." + triggerKey + "[" + index + "] requires an 'id' string");
                        }
                        Map<String, Object> arguments = loadArguments(entry.get("arguments"));
                        bindings.add(new MechanicBinding(
                                itemId,
                                trigger,
                                mechanicId(id, triggerKey, index, idValue),
                                arguments));
                    } else {
                        throw new YamlDefinitionException("Invalid definitions.yml: items." + id
                                + ".mechanics." + triggerKey + "[" + index + "] must be a string or a map");
                    }
                }
            }
        }
        return new MechanicBindingRegistry(bindings);
    }

    private Map<String, Object> loadArguments(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new YamlDefinitionException("Invalid definitions.yml: mechanic 'arguments' must be a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
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

    private MechanicTrigger mechanicTrigger(String itemId, String triggerKey) {
        try {
            return MechanicTrigger.fromYamlKey(triggerKey);
        } catch (IllegalArgumentException exception) {
            throw new YamlDefinitionException("Invalid definitions.yml: items." + itemId + ".mechanics." + triggerKey
                    + " is an unknown mechanic trigger");
        }
    }

    private MechanicId mechanicId(String itemId, String triggerKey, int index, String id) {
        try {
            return new MechanicId(id);
        } catch (IllegalArgumentException exception) {
            throw new YamlDefinitionException("Invalid definitions.yml: items." + itemId + ".mechanics."
                    + triggerKey + "[" + index + "] has invalid mechanic id: " + exception.getMessage());
        }
    }
}
