package com.customcontentengine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.base.BasePaperIntegrationTest;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("protection")
class ProtectionIntegrationTest extends BasePaperIntegrationTest {

    private static final WorldPosition VEIN_START = new WorldPosition("world", 30, 64, 30);
    private static final int PROTECTED_MIN_X = 32;

    @Test
    void veinMinerSkipsProtectedBlocks() throws Exception {
        List<WorldPosition> vein = linearVein(VEIN_START, 5);
        for (WorldPosition position : vein) {
            placeBlock("vein_test_ore", position);
        }
        for (WorldPosition position : vein) {
            awaitBlockState(position, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        }

        sendCommand("testprotection enable " + PROTECTED_MIN_X);
        mineBlock("vein_pickaxe", VEIN_START);

        WorldPosition firstUnprotected = vein.get(0);
        WorldPosition secondUnprotected = vein.get(1);
        WorldPosition firstProtected = vein.get(2);
        WorldPosition lastProtected = vein.get(4);

        awaitBlockState(firstUnprotected, "none", "AIR", Duration.ofSeconds(30));
        awaitBlockState(secondUnprotected, "none", "AIR", Duration.ofSeconds(30));
        awaitBlockState(firstProtected, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        awaitBlockState(lastProtected, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during protection integration%n" + fullOutput());
    }

    private static List<WorldPosition> linearVein(WorldPosition origin, int length) {
        java.util.ArrayList<WorldPosition> positions = new java.util.ArrayList<>(length);
        for (int dx = 0; dx < length; dx++) {
            positions.add(new WorldPosition(
                    origin.worldName(), origin.x() + dx, origin.y(), origin.z()));
        }
        return positions;
    }
}
