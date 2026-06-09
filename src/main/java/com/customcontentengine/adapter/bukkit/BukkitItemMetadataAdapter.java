package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.durability.ToolDurability;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class BukkitItemMetadataAdapter implements ItemMetadataPort<ItemStack> {
    private static final String CUSTOM_ITEM_ID_KEY = "custom_item_id";
    private static final String TOOL_DURABILITY_KEY = "tool_durability";

    private final NamespacedKey customItemIdKey;
    private final NamespacedKey durabilityKey;
    private final Function<Material, ItemStack> itemFactory;
    private final Predicate<Material> itemMaterialPredicate;

    public BukkitItemMetadataAdapter(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.customItemIdKey = new NamespacedKey(plugin, CUSTOM_ITEM_ID_KEY);
        this.durabilityKey = new NamespacedKey(plugin, TOOL_DURABILITY_KEY);
        this.itemFactory = ItemStack::new;
        this.itemMaterialPredicate = Material::isItem;
    }

    BukkitItemMetadataAdapter(NamespacedKey customItemIdKey, NamespacedKey durabilityKey) {
        this.customItemIdKey = Objects.requireNonNull(customItemIdKey, "customItemIdKey");
        this.durabilityKey = Objects.requireNonNull(durabilityKey, "durabilityKey");
        this.itemFactory = ItemStack::new;
        this.itemMaterialPredicate = Material::isItem;
    }

    BukkitItemMetadataAdapter(
            NamespacedKey customItemIdKey,
            NamespacedKey durabilityKey,
            Function<Material, ItemStack> itemFactory,
            Predicate<Material> itemMaterialPredicate) {
        this.customItemIdKey = Objects.requireNonNull(customItemIdKey, "customItemIdKey");
        this.durabilityKey = Objects.requireNonNull(durabilityKey, "durabilityKey");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.itemMaterialPredicate = Objects.requireNonNull(itemMaterialPredicate, "itemMaterialPredicate");
    }

    @Override
    public ItemStack createCustomItem(ItemDef definition) {
        Objects.requireNonNull(definition, "definition");
        Material material = Material.matchMaterial(definition.materialBase());
        if (material == null || !itemMaterialPredicate.test(material)) {
            throw new IllegalArgumentException("Invalid item material_base: " + definition.materialBase());
        }

        ItemStack item = itemFactory.apply(material);
        item.editMeta(meta -> {
            meta.setCustomModelData(definition.customModelData());
            meta.getPersistentDataContainer().set(customItemIdKey, PersistentDataType.STRING, definition.id().value());
        });
        if (definition.durability().isPresent()) {
            ToolDurability initial = initialDurabilityFor(definition.durability().get().max());
            item = writeCurrentDurability(item, initial);
        }
        return item;
    }

    @Override
    public ItemStack applyCustomItemIdentity(ItemStack item, CustomItemId id) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(id, "id");
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(customItemIdKey, PersistentDataType.STRING, id.value()));
        return item;
    }

    @Override
    public Optional<CustomItemId> readCustomItemIdentity(ItemStack item) {
        Objects.requireNonNull(item, "item");
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(customItemIdKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new CustomItemId(value));
    }

    @Override
    public ToolDurability initialDurabilityFor(int max) {
        return new ToolDurability(max, max);
    }

    @Override
    public Optional<ToolDurability> readCurrentDurability(ItemStack item, int max) {
        Objects.requireNonNull(item, "item");
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        Integer current = item.getItemMeta().getPersistentDataContainer().get(durabilityKey, PersistentDataType.INTEGER);
        if (current == null) {
            return Optional.empty();
        }
        return Optional.of(new ToolDurability(max, current));
    }

    @Override
    public ItemStack writeCurrentDurability(ItemStack item, ToolDurability durability) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(durability, "durability");
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(durabilityKey, PersistentDataType.INTEGER, durability.current()));
        return item;
    }
}
