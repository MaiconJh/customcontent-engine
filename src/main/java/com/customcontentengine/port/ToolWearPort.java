package com.customcontentengine.port;

import com.customcontentengine.domain.durability.ToolWearResult;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Optional;

public interface ToolWearPort {
    Optional<ToolWearResult> applyWearIfNeeded(String actorKey, CustomItemId toolId);

    /**
     * Applies wear {@code count} times, once per broken block. Used by mechanics
     * such as {@code vein_miner} when {@code durability_per_block} is enabled.
     *
     * @param count number of blocks broken; values {@code <= 0} are ignored
     * @return the result of the last wear application, if any
     */
    default Optional<ToolWearResult> applyWearIfNeeded(String actorKey, CustomItemId toolId, int count) {
        if (count <= 0) {
            return Optional.empty();
        }
        Optional<ToolWearResult> last = Optional.empty();
        for (int i = 0; i < count; i++) {
            last = applyWearIfNeeded(actorKey, toolId);
            if (last.isEmpty()) {
                break;
            }
        }
        return last;
    }
}