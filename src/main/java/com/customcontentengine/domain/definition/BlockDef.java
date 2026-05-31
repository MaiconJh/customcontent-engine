package com.customcontentengine.domain.definition;

import com.customcontentengine.internalapi.identity.CustomBlockId;
import java.util.Objects;

public record BlockDef(
        CustomBlockId id,
        short numericId,
        String materialBase,
        int customModelData,
        String requiredTool,
        DropTable drops
) {
    public BlockDef {
        Objects.requireNonNull(id, "id");
        if (numericId <= 0) {
            throw new IllegalArgumentException("numericId must be positive");
        }
        if (materialBase == null || materialBase.isBlank()) {
            throw new IllegalArgumentException("materialBase must not be blank");
        }
        if (customModelData <= 0) {
            throw new IllegalArgumentException("customModelData must be positive");
        }
        if (requiredTool == null || requiredTool.isBlank()) {
            throw new IllegalArgumentException("requiredTool must not be blank");
        }
        drops = Objects.requireNonNull(drops, "drops");
    }
}
