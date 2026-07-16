package com.customcontentengine.integration.base;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.harness.PaperServer;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class BasePaperIntegrationTest {
    protected static final Duration SERVER_START_TIMEOUT = Duration.ofMinutes(6);
    protected static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    protected static PaperServer server;
    private static Path serverDirectory;
    private static java.util.Map<String, String> integrationTestSystemProperties;

    @BeforeAll
    static void startPaperServer() throws Exception {
        Path pluginJar = requiredPathProperty("customcontent.pluginJar");
        Path paperJar = requiredPathProperty("customcontent.paperJar");
        serverDirectory = Files.createTempDirectory("customcontent-paper-it-");
        PaperServer.prepareServerDirectory(serverDirectory, pluginJar);
        java.util.Map<String, String> properties = integrationTestSystemProperties != null ? integrationTestSystemProperties : new java.util.HashMap<>();
        server = PaperServer.start(serverDirectory, paperJar, properties);
        server.awaitOutput(line -> line.contains("Done ("), SERVER_START_TIMEOUT);
    }

    @AfterAll
    static void stopPaperServer() throws Exception {
        if (server != null) {
            server.close();
            server = null;
        }
        if (serverDirectory != null) {
            PaperServer.deleteRecursively(serverDirectory);
            serverDirectory = null;
        }
    }

    protected static void setIntegrationTestSystemProperties(java.util.Map<String, String> properties) {
        integrationTestSystemProperties = properties;
    }

    protected static Path serverDirectory() {
        return serverDirectory;
    }

    protected static void sendCommand(String command) throws java.io.IOException {
        server.sendCommand(command);
    }

    protected static void awaitOutput(Predicate<String> predicate, Duration timeout) throws InterruptedException {
        server.awaitOutput(predicate, timeout);
    }

    protected static boolean outputContains(String text) {
        return server.outputContains(text);
    }

    protected static void clearOutput() {
        server.clearOutput();
    }

    protected static String fullOutput() {
        return server.fullOutput();
    }

    protected static void placeBlock(String blockId, WorldPosition position) throws Exception {
        sendCommand("debugplace " + blockId + " " + position.x() + " " + position.y() + " " + position.z() + " " + position.worldName());
        awaitBlockState(position, "none", "AIR", COMMAND_TIMEOUT);
    }

    protected static void mineBlock(String toolId, WorldPosition position) throws Exception {
        sendCommand("debugmine " + toolId + " " + position.x() + " " + position.y() + " " + position.z() + " " + position.worldName());
    }

    protected static void awaitBlockState(WorldPosition position, String expectedNumericId, String expectedMaterial, Duration timeout) throws InterruptedException {
        awaitOutput(line -> line.contains("x=" + position.x() + " y=" + position.y() + " z=" + position.z() + " numericId=" + expectedNumericId + " material=" + expectedMaterial), timeout);
    }

    /**
     * Validates that the registry loaded by the running server contains the given item bound to
     * the given mechanic. Relies on the {@code debugregistry} dev command emitting lines of the
     * form {@code [registry] item=<id> trigger=<key> mechanic=<id>}.
     */
    protected static void assertRegistryContains(String itemId, String mechanicId) throws Exception {
        sendCommand("debugregistry");
        awaitOutput(line -> line.contains("item=" + itemId), COMMAND_TIMEOUT);
        assertTrue(
                outputContains("item=" + itemId),
                () -> "Registry missing item: " + itemId + "%n%s".formatted(fullOutput()));
        assertTrue(
                outputContains("mechanic=" + mechanicId),
                () -> "Registry missing mechanic binding: " + mechanicId + "%n%s".formatted(fullOutput()));
    }

    protected static Path requiredPathProperty(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + value);
        }
        return Path.of(value);
    }
}
