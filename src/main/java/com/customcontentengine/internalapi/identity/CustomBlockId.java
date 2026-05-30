package com.customcontentengine.internalapi.identity;

import java.util.Objects;
import java.util.regex.Pattern;

public record CustomBlockId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]*");

    public CustomBlockId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Custom block id must match [a-z][a-z0-9_]*");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
