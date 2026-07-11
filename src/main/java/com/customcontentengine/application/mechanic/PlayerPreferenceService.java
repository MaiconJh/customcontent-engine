package com.customcontentengine.application.mechanic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of per-player preferences for the {@code vein_miner} mechanic.
 *
 * <p>Kept in the application layer so the domain and mechanics remain free of
 * player session state. Players are enabled by default; the toggle command
 * flips the stored preference.</p>
 */
public final class PlayerPreferenceService {
    private final Map<String, Boolean> preferences = new ConcurrentHashMap<>();

    public boolean isEnabled(String actorKey) {
        return preferences.getOrDefault(actorKey, Boolean.TRUE);
    }

    public void setEnabled(String actorKey, boolean enabled) {
        preferences.put(actorKey, enabled);
    }

    public boolean toggle(String actorKey) {
        boolean next = !isEnabled(actorKey);
        setEnabled(actorKey, next);
        return next;
    }
}
