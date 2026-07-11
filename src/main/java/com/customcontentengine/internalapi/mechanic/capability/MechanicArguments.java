package com.customcontentengine.internalapi.mechanic.capability;

import java.util.Map;
import java.util.Optional;

/**
 * Module capability that exposes the per-binding YAML arguments of a mechanic
 * (e.g. {@code max_blocks}, {@code max_depth}, {@code shape}) to the executing
 * mechanic, without coupling the mechanic to Bukkit, the loader, or the domain.
 *
 * <p>Classified as a module capability (ARCHITECTURE_GUARDRAILS.md 14.2): it is
 * specialised for mechanics such as {@code vein_miner} that accept per-item
 * configuration and must not be promoted to the stable core without broader
 * justification.</p>
 */
public interface MechanicArguments {
    Optional<Object> get(String key);

    Map<String, Object> all();
}
