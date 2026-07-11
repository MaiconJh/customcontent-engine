package com.customcontentengine.internalapi.mechanic.capability;

import java.util.Optional;

public interface MechanicConfig {
    Optional<String> getString(String key);
    Optional<Integer> getInt(String key);
    Optional<Boolean> getBoolean(String key);
    Optional<Double> getDouble(String key);
}