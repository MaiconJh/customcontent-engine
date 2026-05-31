package com.customcontentengine.port;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Optional;

public interface ItemMetadataPort<T> {
    T createCustomItem(ItemDef definition);

    T applyCustomItemIdentity(T item, CustomItemId id);

    Optional<CustomItemId> readCustomItemIdentity(T item);
}
