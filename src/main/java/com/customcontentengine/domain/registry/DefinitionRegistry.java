package com.customcontentengine.domain.registry;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DefinitionRegistry {
    private final Map<String, BlockDef> blocksById;
    private final Map<Short, BlockDef> blocksByNumericId;
    private final Map<String, ItemDef> itemsById;
    private final MechanicBindingRegistry mechanicBindings;

    public DefinitionRegistry(Collection<BlockDef> blocks, Collection<ItemDef> items) {
        this(blocks, items, MechanicBindingRegistry.empty());
    }

    public DefinitionRegistry(
            Collection<BlockDef> blocks,
            Collection<ItemDef> items,
            MechanicBindingRegistry mechanicBindings) {
        Map<String, BlockDef> blockIds = new LinkedHashMap<>();
        Map<Short, BlockDef> numericIds = new LinkedHashMap<>();
        for (BlockDef block : blocks == null ? java.util.List.<BlockDef>of() : blocks) {
            if (blockIds.putIfAbsent(block.id().value(), block) != null) {
                throw new IllegalArgumentException("Duplicate block id: " + block.id());
            }
            if (numericIds.putIfAbsent(block.numericId(), block) != null) {
                throw new IllegalArgumentException("Duplicate block numeric_id: " + block.numericId());
            }
        }

        Map<String, ItemDef> itemIds = new LinkedHashMap<>();
        for (ItemDef item : items == null ? java.util.List.<ItemDef>of() : items) {
            if (itemIds.putIfAbsent(item.id().value(), item) != null) {
                throw new IllegalArgumentException("Duplicate item id: " + item.id());
            }
        }

        this.blocksById = Map.copyOf(blockIds);
        this.blocksByNumericId = Map.copyOf(numericIds);
        this.itemsById = Map.copyOf(itemIds);
        this.mechanicBindings = java.util.Objects.requireNonNull(mechanicBindings, "mechanicBindings");
    }

    public Optional<BlockDef> findBlock(String id) {
        return Optional.ofNullable(blocksById.get(id));
    }

    public Optional<BlockDef> findBlock(CustomBlockId id) {
        return findBlock(id.value());
    }

    public Optional<BlockDef> findBlockByNumericId(short numericId) {
        return Optional.ofNullable(blocksByNumericId.get(numericId));
    }

    public Optional<ItemDef> findItem(String id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    public Optional<ItemDef> findItem(CustomItemId id) {
        return findItem(id.value());
    }

    public Map<String, BlockDef> blocksById() {
        return blocksById;
    }

    public Map<Short, BlockDef> blocksByNumericId() {
        return blocksByNumericId;
    }

    public Map<String, ItemDef> itemsById() {
        return itemsById;
    }

    public MechanicBindingRegistry mechanicBindings() {
        return mechanicBindings;
    }
}
