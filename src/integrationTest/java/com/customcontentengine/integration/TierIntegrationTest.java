package com.customcontentengine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.base.BasePaperIntegrationTest;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tier")
class TierIntegrationTest extends BasePaperIntegrationTest {

    private static final WorldPosition POSITION = new WorldPosition("world", 20, 64, 20);

    @Test
    void debugmineRejectsWhenToolTierIsTooLow() throws Exception {
        placeBlock("iron_ore", POSITION);
        awaitBlockState(POSITION, "2", "IRON_ORE", Duration.ofSeconds(10));

        sendCommand("debugmine stone_pickaxe " + POSITION.x() + " " + POSITION.y() + " " + POSITION.z() + " " + POSITION.worldName());
        awaitOutput(line -> line.contains("debugmine rejected: tool tier cannot mine this block."), Duration.ofSeconds(10));
        assertTrue(
                outputContains("debugmine rejected: tool tier cannot mine this block."),
                () -> "Expected tier rejection message%n" + fullOutput());

        awaitBlockState(POSITION, "2", "IRON_ORE", Duration.ofSeconds(10));
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during tier rejection%n" + fullOutput());
    }

    @Test
    void debugmineSucceedsWhenToolTierMatches() throws Exception {
        placeBlock("iron_ore", POSITION);
        awaitBlockState(POSITION, "2", "IRON_ORE", Duration.ofSeconds(10));

        mineBlock("iron_pickaxe", POSITION);

        awaitBlockState(POSITION, "none", "AIR", Duration.ofSeconds(30));
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during tier success%n" + fullOutput());
    }
}
