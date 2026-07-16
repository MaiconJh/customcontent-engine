package com.customcontentengine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.customcontentengine.integration.base.BasePaperIntegrationTest;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("mechanic")
class MechanicTriggerIntegrationTest extends BasePaperIntegrationTest {

    private static final WorldPosition AREA_ORIGIN = new WorldPosition("world", 10, 64, 10);
    private static final WorldPosition TRANSFORM_ORIGIN = new WorldPosition("world", 20, 64, 20);

    @Test
    void areaBreakBreaksFlatThreeByThreeAroundOrigin() throws Exception {
        List<WorldPosition> area = flatArea(AREA_ORIGIN);
        for (WorldPosition position : area) {
            placeBlock("ruby_ore", position);
        }
        for (WorldPosition position : area) {
            awaitBlockState(position, "3", "NOTE_BLOCK", Duration.ofSeconds(30));
        }

        mineBlock("ruby_pickaxe", AREA_ORIGIN);

        for (WorldPosition position : area) {
            awaitBlockState(position, "none", "AIR", Duration.ofSeconds(60));
        }
        assertFalse(
                server.outputContains("Error occurred while enabling"),
                () -> "Unexpected error during area_break%n" + fullOutput());
    }

    @Test
    void blockTransformReplacesOriginBlock() throws Exception {
        placeBlock("transform_target", TRANSFORM_ORIGIN);
        awaitBlockState(TRANSFORM_ORIGIN, "5", "STONE", Duration.ofSeconds(30));

        mineBlock("transform_tool", TRANSFORM_ORIGIN);

        awaitBlockState(TRANSFORM_ORIGIN, "1", "NOTE_BLOCK", Duration.ofSeconds(60));
    }

    private static List<WorldPosition> flatArea(WorldPosition origin) {
        List<WorldPosition> positions = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                positions.add(new WorldPosition(
                        origin.worldName(), origin.x() + dx, origin.y(), origin.z() + dz));
            }
        }
        return positions;
    }
}
