package com.customcontentengine.domain.mining;

public record MiningSessionId(String value) {
    public MiningSessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mining session id must not be blank");
        }
    }
}
