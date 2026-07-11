package com.customcontentengine.adapter.platform;

import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import java.util.Objects;
import java.util.OptionalInt;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Paper/Bukkit implementation of {@link EnchantmentView} that reads enchantment
 * levels from an {@link ItemStack}. Confined to the adapter layer so the domain
 * and mechanics never depend on Bukkit types.
 */
public final class BukkitEnchantmentViewAdapter implements EnchantmentView {
    private final ItemStack item;

    public BukkitEnchantmentViewAdapter(ItemStack item) {
        this.item = Objects.requireNonNull(item, "item");
    }

    @Override
    public OptionalInt getLevel(String enchantmentKey) {
        Objects.requireNonNull(enchantmentKey, "enchantmentKey");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return OptionalInt.empty();
        }
        Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantmentKey));
        if (enchantment == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(meta.getEnchantLevel(enchantment));
    }
}
