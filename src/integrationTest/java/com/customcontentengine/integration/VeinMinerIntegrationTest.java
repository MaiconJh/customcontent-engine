package com.customcontentengine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.base.BasePaperIntegrationTest;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("vein_miner")
class VeinMinerIntegrationTest extends BasePaperIntegrationTest {

    private static final WorldPosition VEIN_START = new WorldPosition("world", 10, 64, 10);

    @Test
    @Tag("smoke")
    void veinMinerBreaksLinearVeinAndRemovesIdentity() throws Exception {
        List<WorldPosition> vein = linearVein(VEIN_START, 5);
        for (WorldPosition position : vein) {
            placeBlock("vein_test_ore", position);
        }
        for (WorldPosition position : vein) {
            awaitBlockState(position, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        }

        mineBlock("vein_pickaxe", VEIN_START);

        for (WorldPosition position : vein) {
            awaitBlockState(position, "none", "AIR", Duration.ofSeconds(30));
        }
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during vein_miner%n" + fullOutput());
    }

    @Test
    void veinMinerRespectsMaxBlocksAndLeavesRemaining() throws Exception {
        List<WorldPosition> vein = linearVein(VEIN_START, 5);
        for (WorldPosition position : vein) {
            placeBlock("vein_test_ore", position);
        }
        for (WorldPosition position : vein) {
            awaitBlockState(position, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        }

        mineBlock("vein_pickaxe", VEIN_START);

        WorldPosition firstRemaining = vein.get(4);
        WorldPosition lastBroken = vein.get(3);
        awaitBlockState(lastBroken, "none", "AIR", Duration.ofSeconds(30));
        awaitBlockState(firstRemaining, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during vein_miner max_blocks%n" + fullOutput());
    }

    @Test
    @Tag("slow")
    void veinMinerPerformanceGate16Blocks() throws Exception {
        long start = System.currentTimeMillis();
        List<WorldPosition> vein = linearVein(VEIN_START, 16);
        for (WorldPosition position : vein) {
            placeBlock("vein_test_ore", position);
        }
        for (WorldPosition position : vein) {
            awaitBlockState(position, "6", "NOTE_BLOCK", Duration.ofSeconds(10));
        }

        mineBlock("vein_pickaxe", VEIN_START);

        for (WorldPosition position : vein) {
            awaitBlockState(position, "none", "AIR", Duration.ofSeconds(30));
        }
        long elapsedMs = System.currentTimeMillis() - start;
        assertTrue(
                elapsedMs < 20_000,
                () -> "vein_miner 16-block performance gate exceeded: " + elapsedMs + "ms");
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during vein_miner performance gate%n" + fullOutput());
    }

    private static List<WorldPosition> linearVein(WorldPosition origin, int length) {
        List<WorldPosition> positions = new ArrayList<>(length);
        for (int dx = 0; dx < length; dx++) {
            positions.add(new WorldPosition(
                    origin.worldName(), origin.x() + dx, origin.y(), origin.z()));
        }
        return positions;
    }
}