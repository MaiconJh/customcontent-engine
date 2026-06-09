package com.customcontentengine.domain.definition;

import com.customcontentengine.domain.durability.ToolDurabilityDefinition;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Objects;
import java.util.Optional;

public record ItemDef(
        CustomItemId id,
        String materialBase,
        int customModelData,
        ToolAttributes attributes,
        Optional<MiningSpeed> miningSpeed,
        Optional<ToolDurabilityDefinition> durability) {
    public ItemDef(
            CustomItemId id,
            String materialBase,
            int customModelData,
            ToolAttributes attributes) {
        this(id, materialBase, customModelData, attributes, Optional.empty(), Optional.empty());
    }

    public ItemDef {
        Objects.requireNonNull(id, "id");
        if (materialBase == null || materialBase.isBlank()) {
            throw new IllegalArgumentException("materialBase must not be blank");
        }
        if (customModelData <= 0) {
            throw new IllegalArgumentException("customModelData must be positive");
        }
        attributes = Objects.requireNonNull(attributes, "attributes");
        miningSpeed = Objects.requireNonNull(miningSpeed, "miningSpeed");
        durability = Objects.requireNonNull(durability, "durability");
    }
}
