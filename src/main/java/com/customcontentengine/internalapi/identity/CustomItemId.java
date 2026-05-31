package com.customcontentengine.internalapi.identity;

import java.util.Objects;
import java.util.regex.Pattern;

public record CustomItemId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]*");

    public CustomItemId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Custom item id must match [a-z][a-z0-9_]*");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
