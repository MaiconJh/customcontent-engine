package com.customcontentengine.internalapi.mechanic.capability;

import java.util.OptionalInt;

/**
 * Module capability that allows a mechanic to query enchantment levels (e.g.
 * {@code fortune}, {@code silk_touch}, {@code unbreaking}) of the acting item
 * without depending on Bukkit/Paper types.
 *
 * <p>Classified as a module capability (see ARCHITECTURE_GUARDRAILS.md 14.2):
 * it is specialised for mechanics such as {@code vein_miner} and must not be
 * promoted to the stable core without broader, cross-mechanic justification.</p>
 */
public interface EnchantmentView {
    OptionalInt getLevel(String enchantmentKey);
}
