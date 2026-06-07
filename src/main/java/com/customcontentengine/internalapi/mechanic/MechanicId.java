package com.customcontentengine.internalapi.mechanic;

import java.util.Objects;
import java.util.regex.Pattern;

public record MechanicId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]*");

    public MechanicId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Mechanic id must match [a-z][a-z0-9_]*");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
