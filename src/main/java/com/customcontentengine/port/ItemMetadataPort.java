package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Optional;

public interface ItemMetadataPort<T> {
    T applyCustomItemIdentity(T item, CustomItemId id);

    Optional<CustomItemId> readCustomItemIdentity(T item);
}
