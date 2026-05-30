package com.customcontentengine.adapter.yaml;

import org.bukkit.configuration.ConfigurationSection;

public final class YamlDefinitionValidator {
    public void validate(ConfigurationSection root) {
        if (root == null) {
            throw new IllegalArgumentException("definitions.yml root is missing");
        }
        if (root.getInt("schema", -1) != 1) {
            throw new IllegalArgumentException("definitions.yml schema must be 1");
        }
        ConfigurationSection blocks = root.getConfigurationSection("blocks");
        if (blocks == null || blocks.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("definitions.yml must define at least one block");
        }
        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null || items.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("definitions.yml must define at least one item");
        }
    }
}
