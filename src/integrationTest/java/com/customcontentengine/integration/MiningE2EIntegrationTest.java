package com.customcontentengine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.base.BasePaperIntegrationTest;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("mining")
class MiningE2EIntegrationTest extends BasePaperIntegrationTest {

    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);

    @Test
    @Tag("smoke")
    void customBlockMiningLifecycleCompletesAndRemovesIdentity() throws Exception {
        placeBlock("stone_quarry", TARGET);
        awaitBlockState(TARGET, "1", "STONE", Duration.ofSeconds(30));
        assertTrue(outputContains("numericId=1"), () -> "Block identity not placed%n" + fullOutput());

        mineBlock("stone_pickaxe", TARGET);
        awaitBlockState(TARGET, "none", "AIR", Duration.ofSeconds(60));

        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during mining lifecycle%n" + fullOutput());
    }

    @Test
    void miningSameBlockTwiceIsIdempotent() throws Exception {
        placeBlock("stone_quarry", TARGET);
        awaitBlockState(TARGET, "1", "STONE", Duration.ofSeconds(30));

        mineBlock("stone_pickaxe", TARGET);
        awaitBlockState(TARGET, "none", "AIR", Duration.ofSeconds(60));

        server.clearOutput();
        mineBlock("stone_pickaxe", TARGET);
        awaitBlockState(TARGET, "none", "AIR", Duration.ofSeconds(60));
        assertFalse(
                outputContains("debugmine failed"),
                () -> "Second mine should not report failure%n" + fullOutput());
    }
}
