package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.durability.ToolDurability;
import com.customcontentengine.domain.durability.ToolWearResult;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.port.ItemMetadataPort;
import com.customcontentengine.port.ToolWearPort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class BukkitToolWearAdapter implements ToolWearPort {
    private final DefinitionRegistry definitions;
    private final ItemMetadataPort<ItemStack> itemMetadata;

    public BukkitToolWearAdapter(DefinitionRegistry definitions, ItemMetadataPort<ItemStack> itemMetadata) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.itemMetadata = Objects.requireNonNull(itemMetadata, "itemMetadata");
    }

    @Override
    public Optional<ToolWearResult> applyWearIfNeeded(String actorKey, CustomItemId toolId) {
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(toolId, "toolId");

        Optional<ItemDef> definition = definitions.findItem(toolId);
        if (definition.isEmpty() || definition.get().durability().isEmpty()) {
            return Optional.empty();
        }

        int maxDurability = definition.get().durability().get().max();
        UUID actorUuid;
        try {
            actorUuid = UUID.fromString(actorKey);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        Player player = Bukkit.getPlayer(actorUuid);
        if (player == null) {
            return Optional.empty();
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<CustomItemId> heldItemId = itemMetadata.readCustomItemIdentity(item);
        if (heldItemId.isEmpty() || !heldItemId.get().equals(toolId)) {
            return Optional.empty();
        }

        Optional<ToolDurability> currentDurability = itemMetadata.readCurrentDurability(item, maxDurability);
        ToolDurability current = currentDurability.orElseGet(() -> {
            ToolDurability initial = itemMetadata.initialDurabilityFor(maxDurability);
            itemMetadata.writeCurrentDurability(item, initial);
            return initial;
        });

        ToolWearResult result = definition.get().durability().get().applyWear(current);
        if (!result.shouldBreak()) {
            itemMetadata.writeCurrentDurability(item, result.newDurability());
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        return Optional.of(result);
    }
}