package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.internalapi.mechanic.capability.MechanicConfig;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MappedMechanicConfig implements MechanicConfig {
    private final Map<String, Object> arguments;

    public MappedMechanicConfig(Map<String, Object> arguments) {
        this.arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    @Override
    public Optional<String> getString(String key) {
        Object value = arguments.get(key);
        return value instanceof String s ? Optional.of(s) : Optional.empty();
    }

    @Override
    public Optional<Integer> getInt(String key) {
        Object value = arguments.get(key);
        if (value instanceof Number n) {
            return Optional.of(n.intValue());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        Object value = arguments.get(key);
        if (value instanceof Boolean b) {
            return Optional.of(b);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Double> getDouble(String key) {
        Object value = arguments.get(key);
        if (value instanceof Number n) {
            return Optional.of(n.doubleValue());
        }
        return Optional.empty();
    }
}