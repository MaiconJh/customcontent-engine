package com.customcontentengine.application.item;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.Objects;
import java.util.Optional;

public final class ItemService<T> {
    private final DefinitionRegistry definitions;
    private final ItemMetadataPort<T> itemMetadata;

    public ItemService(DefinitionRegistry definitions, ItemMetadataPort<T> itemMetadata) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.itemMetadata = Objects.requireNonNull(itemMetadata, "itemMetadata");
    }

    public Optional<ItemDef> findItem(CustomItemId id) {
        return definitions.findItem(id);
    }

    public ItemCreationResult<T> createCustomItem(String rawId) {
        CustomItemId id;
        try {
            id = new CustomItemId(rawId);
        } catch (IllegalArgumentException exception) {
            return ItemCreationResult.invalidId("Invalid custom item id: " + rawId);
        }

        Optional<ItemDef> definition = definitions.findItem(id);
        if (definition.isEmpty()) {
            return ItemCreationResult.unknownItem("Unknown custom item: " + id.value());
        }

        try {
            return ItemCreationResult.success(itemMetadata.createCustomItem(definition.get()));
        } catch (RuntimeException exception) {
            return ItemCreationResult.creationFailed(
                    "Could not create custom item " + id.value() + ": " + exception.getMessage());
        }
    }

    public enum ItemCreationStatus {
        SUCCESS,
        INVALID_ID,
        UNKNOWN_ITEM,
        CREATION_FAILED
    }

    public record ItemCreationResult<T>(ItemCreationStatus status, Optional<T> item, String message) {
        public ItemCreationResult {
            Objects.requireNonNull(status, "status");
            item = Objects.requireNonNull(item, "item");
            message = Objects.requireNonNull(message, "message");
            if (status == ItemCreationStatus.SUCCESS && item.isEmpty()) {
                throw new IllegalArgumentException("successful item creation must include an item");
            }
            if (status != ItemCreationStatus.SUCCESS && item.isPresent()) {
                throw new IllegalArgumentException("failed item creation must not include an item");
            }
        }

        public static <T> ItemCreationResult<T> success(T item) {
            return new ItemCreationResult<>(ItemCreationStatus.SUCCESS, Optional.of(item), "Custom item created.");
        }

        public static <T> ItemCreationResult<T> invalidId(String message) {
            return new ItemCreationResult<>(ItemCreationStatus.INVALID_ID, Optional.empty(), message);
        }

        public static <T> ItemCreationResult<T> unknownItem(String message) {
            return new ItemCreationResult<>(ItemCreationStatus.UNKNOWN_ITEM, Optional.empty(), message);
        }

        public static <T> ItemCreationResult<T> creationFailed(String message) {
            return new ItemCreationResult<>(ItemCreationStatus.CREATION_FAILED, Optional.empty(), message);
        }
    }
}
