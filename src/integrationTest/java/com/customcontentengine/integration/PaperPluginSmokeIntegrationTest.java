package com.customcontentengine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.base.BasePaperIntegrationTest;
import java.nio.file.Files;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class PaperPluginSmokeIntegrationTest extends BasePaperIntegrationTest {

    @Test
    @Tag("smoke")
    void paperServerLoadsPluginDefinitionsAndDebugCommand() throws Exception {
        assertTrue(
                Files.isRegularFile(
                        serverDirectory().resolve("plugins/CustomContentEngine/definitions.yml")),
                "definitions.yml should be copied to the plugin data folder");

        sendCommand("givecustomitem");
        awaitOutput(
                line -> line.contains("Only players can use this debug command."),
                COMMAND_TIMEOUT);

        assertRegistryContains("ruby_pickaxe", "area_break");
    }
}
