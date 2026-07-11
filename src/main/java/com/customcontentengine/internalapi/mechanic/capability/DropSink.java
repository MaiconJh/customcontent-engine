package com.customcontentengine.internalapi.mechanic.capability;

import com.customcontentengine.internalapi.identity.WorldPosition;

public interface DropSink {
    void dropFor(WorldPosition position, short numericId);

    /**
     * Drops the given number of items for the custom block at {@code position}.
     * The default implementation ignores {@code count} and delegates to
     * {@link #dropFor(WorldPosition, short)}, preserving backward compatibility
     * for sinks that do not support quantity. Sinks that apply enchantments
     * (e.g. Fortune) should override this method.
     *
     * @param position  the block position
     * @param numericId the custom block numeric id
     * @param count     the number of items to drop (must be positive)
     */
    default void dropFor(WorldPosition position, short numericId, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        dropFor(position, numericId);
    }
}
