package com.customcontentengine.domain.mechanic;

import java.util.Arrays;

public enum MechanicTrigger {
    ON_BLOCK_BREAK("on_block_break");

    private final String yamlKey;

    MechanicTrigger(String yamlKey) {
        this.yamlKey = yamlKey;
    }

    public String yamlKey() {
        return yamlKey;
    }

    public static MechanicTrigger fromYamlKey(String yamlKey) {
        return Arrays.stream(values())
                .filter(trigger -> trigger.yamlKey.equals(yamlKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown mechanic trigger: " + yamlKey));
    }
}
