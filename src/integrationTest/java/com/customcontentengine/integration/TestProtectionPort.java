package com.customcontentengine.integration;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.ProtectionPort;

public final class TestProtectionPort implements ProtectionPort {
    private final int protectedMinX;

    public TestProtectionPort(int protectedMinX) {
        this.protectedMinX = protectedMinX;
    }

    @Override
    public boolean canBuild(String actorName, WorldPosition position) {
        return position.x() < protectedMinX;
    }
}
