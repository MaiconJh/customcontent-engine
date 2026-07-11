package com.customcontentengine.internalapi.mechanic.capability;

/**
 * Module capability that exposes the acting player's current state (e.g. whether
 * they are sneaking) to a mechanic, without depending on Bukkit/Paper types.
 *
 * <p>Classified as a module capability (see ARCHITECTURE_GUARDRAILS.md 14.2):
 * it is specialised for mechanics such as {@code vein_miner} that need to react
 * to the actor's state and must not be promoted to the stable core without
 * broader, cross-mechanic justification.</p>
 */
public interface ActorState {
    boolean isSneaking();
}
