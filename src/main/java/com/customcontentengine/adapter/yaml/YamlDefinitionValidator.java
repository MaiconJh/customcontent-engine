package com.customcontentengine.adapter.yaml;

import com.customcontentengine.domain.registry.DefinitionRegistry;
import org.bukkit.configuration.ConfigurationSection;

public final class YamlDefinitionValidator {
    public void validateRoot(ConfigurationSection root) {
        if (root == null) {
            throw error("definitions.yml root is missing");
        }
        requireInt(root, "schema");
        int schema = root.getInt("schema");
        if (schema != 1) {
            throw error("schema must be 1 but was " + schema);
        }
        requireSection(root, "blocks");
        requireSection(root, "items");
    }

    public void validateBlockSection(String id, ConfigurationSection section) {
        requireSectionInstance(section, "blocks." + id);
        requireInt(section, "blocks." + id + ".numeric_id", "numeric_id");
        int numericId = section.getInt("numeric_id");
        if (numericId <= 0 || numericId > Short.MAX_VALUE) {
            throw error("blocks." + id + ".numeric_id must be between 1 and " + Short.MAX_VALUE + " but was " + numericId);
        }
        requireNonBlankString(section, "blocks." + id + ".material_base", "material_base");
        requirePositiveInt(section, "blocks." + id + ".custom_model_data", "custom_model_data");
        requireNonBlankString(section, "blocks." + id + ".required_tool", "required_tool");
        if (!section.isList("drops")) {
            throw error("blocks." + id + ".drops must be a list");
        }
        if (section.getMapList("drops").isEmpty()) {
            throw error("blocks." + id + ".drops must contain at least one drop");
        }
        if (section.contains("mining")) {
            ConfigurationSection mining = requireSection(section, "blocks." + id + ".mining", "mining");
            requireNumber(mining, "blocks." + id + ".mining.hardness", "hardness");
            double hardness = mining.getDouble("hardness");
            if (hardness <= 0.0D) {
                throw error("blocks." + id + ".mining.hardness must be greater than zero but was " + hardness);
            }
            if (mining.contains("required_tier")) {
                requirePositiveInt(mining, "blocks." + id + ".mining.required_tier", "required_tier");
            }
        }
    }

    public void validateDrop(String blockId, int index, Object item, Object amount) {
        String path = "blocks." + blockId + ".drops[" + index + "]";
        if (!(item instanceof String itemText) || itemText.isBlank()) {
            throw error(path + ".item must be a non-empty string");
        }
        if (!(amount instanceof Number number)) {
            throw error(path + ".amount must be a number");
        }
        if (number.intValue() <= 0) {
            throw error(path + ".amount must be greater than zero but was " + number.intValue());
        }
    }

    public void validateItemSection(String id, ConfigurationSection section) {
        requireSectionInstance(section, "items." + id);
        requireNonBlankString(section, "items." + id + ".material_base", "material_base");
        requirePositiveInt(section, "items." + id + ".custom_model_data", "custom_model_data");
        if (section.contains("attributes")) {
            ConfigurationSection attributes = requireSection(section, "items." + id + ".attributes", "attributes");
            requireNumber(attributes, "items." + id + ".attributes.damage", "damage");
            requireNumber(attributes, "items." + id + ".attributes.speed", "speed");
            requirePositiveInt(attributes, "items." + id + ".attributes.durability", "durability");
        }
        if (section.contains("mining")) {
            ConfigurationSection mining = requireSection(section, "items." + id + ".mining", "mining");
            requireNumber(mining, "items." + id + ".mining.speed", "speed");
            double speed = mining.getDouble("speed");
            if (speed <= 0.0D) {
                throw error("items." + id + ".mining.speed must be greater than zero but was " + speed);
            }
            if (mining.contains("tier")) {
                requirePositiveInt(mining, "items." + id + ".mining.tier", "tier");
            }
        }
        if (section.contains("durability")) {
            ConfigurationSection durability = requireSection(section, "items." + id + ".durability", "durability");
            requireInt(durability, "items." + id + ".durability.max", "max");
            int max = durability.getInt("max");
            if (max <= 0) {
                throw error("items." + id + ".durability.max must be greater than zero but was " + max);
            }
            if (durability.isInt("damage_on_custom_block_break")) {
                int damage = durability.getInt("damage_on_custom_block_break");
                if (damage < 0) {
                    throw error("items." + id + ".durability.damage_on_custom_block_break must not be negative but was " + damage);
                }
            }
        }
        if (section.contains("mechanics")) {
            ConfigurationSection mechanics = requireSection(section, "items." + id + ".mechanics", "mechanics");
            for (String trigger : mechanics.getKeys(false)) {
                if (!trigger.equals("on_block_break")) {
                    throw error("items." + id + ".mechanics." + trigger + " is an unknown mechanic trigger");
                }
                if (!mechanics.isList(trigger)) {
                    throw error("items." + id + ".mechanics." + trigger + " must be a list");
                }
                java.util.List<?> mechanicIds = mechanics.getList(trigger);
                if (mechanicIds == null || mechanicIds.isEmpty()) {
                    throw error("items." + id + ".mechanics." + trigger + " must contain at least one mechanic id");
                }
                for (int index = 0; index < mechanicIds.size(); index++) {
                    Object mechanicId = mechanicIds.get(index);
                    if (mechanicId instanceof String mechanicIdText) {
                        if (mechanicIdText.isBlank()) {
                            throw error("items." + id + ".mechanics." + trigger + "[" + index + "] must be a non-empty string");
                        }
                    } else if (mechanicId instanceof java.util.Map<?, ?> mechanicEntry) {
                        Object rawId = mechanicEntry.get("id");
                        if (!(rawId instanceof String mechanicIdText) || mechanicIdText.isBlank()) {
                            throw error("items." + id + ".mechanics." + trigger + "[" + index + "].id must be a non-empty string");
                        }
                        Object rawArguments = mechanicEntry.get("arguments");
                        if (rawArguments != null && !(rawArguments instanceof java.util.Map<?, ?>)) {
                            throw error("items." + id + ".mechanics." + trigger + "[" + index + "].arguments must be a map");
                        }
                    } else {
                        throw error("items." + id + ".mechanics." + trigger + "[" + index + "] must be a non-empty string or a map with 'id'");
                    }
                }
            }
        }
    }

    public void validateCrossReferences(DefinitionRegistry registry) {
        registry.blocksById().values().forEach(block -> {
            if (registry.findItem(block.requiredTool()).isEmpty()) {
                throw error("blocks." + block.id() + ".required_tool references unknown item: " + block.requiredTool());
            }
        });
    }

    private ConfigurationSection requireSection(ConfigurationSection parent, String key) {
        return requireSection(parent, key, key);
    }

    private ConfigurationSection requireSection(ConfigurationSection parent, String path, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw error(path + " must be a YAML section");
        }
        return section;
    }

    private void requireSectionInstance(ConfigurationSection section, String path) {
        if (section == null) {
            throw error(path + " must be a YAML section");
        }
    }

    private void requireInt(ConfigurationSection section, String path) {
        requireInt(section, path, path);
    }

    private void requireInt(ConfigurationSection section, String path, String key) {
        if (!section.isInt(key)) {
            throw error(path + " must be an integer");
        }
    }

    private void requirePositiveInt(ConfigurationSection section, String path, String key) {
        requireInt(section, path, key);
        int value = section.getInt(key);
        if (value <= 0) {
            throw error(path + " must be greater than zero but was " + value);
        }
    }

    private void requireNumber(ConfigurationSection section, String path, String key) {
        if (!section.isDouble(key) && !section.isInt(key)) {
            throw error(path + " must be a number");
        }
    }

    private void requireNonBlankString(ConfigurationSection section, String path, String key) {
        if (!section.isString(key) || section.getString(key).isBlank()) {
            throw error(path + " must be a non-empty string");
        }
    }

    private YamlDefinitionException error(String message) {
        return new YamlDefinitionException("Invalid definitions.yml: " + message);
    }
}
