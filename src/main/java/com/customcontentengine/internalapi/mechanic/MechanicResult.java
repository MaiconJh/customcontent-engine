package com.customcontentengine.internalapi.mechanic;

public record MechanicResult(boolean handled) {
    public static MechanicResult noop() {
        return new MechanicResult(false);
    }
}
