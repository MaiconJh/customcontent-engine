package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class InMemoryCooldowns {
    private final Map<String, Long> lastExecutionMillisByKey = new HashMap<>();
    private final Clock clock;

    public InMemoryCooldowns() {
        this(Clock.systemUTC());
    }

    InMemoryCooldowns(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CooldownView view(String key, long cooldownMillis) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (cooldownMillis < 0) {
            throw new IllegalArgumentException("cooldownMillis must not be negative");
        }
        return () -> {
            long now = clock.millis();
            Long lastExecutionMillis = lastExecutionMillisByKey.get(key);
            if (lastExecutionMillis != null && now - lastExecutionMillis < cooldownMillis) {
                return false;
            }
            lastExecutionMillisByKey.put(key, now);
            return true;
        };
    }
}
