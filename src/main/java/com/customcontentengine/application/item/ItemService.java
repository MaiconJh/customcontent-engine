package com.customcontentengine.application.item;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Objects;
import java.util.Optional;

public final class ItemService {
    private final DefinitionRegistry definitions;

    public ItemService(DefinitionRegistry definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public Optional<ItemDef> findItem(CustomItemId id) {
        return definitions.findItem(id);
    }
}
