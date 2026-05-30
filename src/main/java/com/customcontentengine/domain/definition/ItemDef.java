package com.customcontentengine.domain.definition;

import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Objects;

public record ItemDef(
        CustomItemId id,
        String materialBase,
        int customModelData,
        ToolAttributes attributes
) {
    public ItemDef {
        Objects.requireNonNull(id, "id");
        if (materialBase == null || materialBase.isBlank()) {
            throw new IllegalArgumentException("materialBase must not be blank");
        }
        if (customModelData < 0) {
            throw new IllegalArgumentException("customModelData must not be negative");
        }
        attributes = Objects.requireNonNull(attributes, "attributes");
    }
}
