package com.customcontentengine.internalapi.mechanic;

import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.List;
import java.util.Objects;

public sealed interface MechanicResult permits MechanicResult.Done, MechanicResult.Partial, MechanicResult.Rejected {
    record Done(int affectedBlocks) implements MechanicResult {
        public Done {
            if (affectedBlocks < 0) {
                throw new IllegalArgumentException("affectedBlocks must not be negative");
            }
        }
    }

    record Partial(int affectedBlocks, List<WorldPosition> remaining) implements MechanicResult {
        public Partial {
            if (affectedBlocks < 0) {
                throw new IllegalArgumentException("affectedBlocks must not be negative");
            }
            remaining = List.copyOf(Objects.requireNonNull(remaining, "remaining"));
        }
    }

    record Rejected(String reason) implements MechanicResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }
}
