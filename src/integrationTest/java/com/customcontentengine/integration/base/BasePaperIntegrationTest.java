package com.customcontentengine.integration.base;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.integration.harness.PaperServer;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class BasePaperIntegrationTest {
    protected static final Duration SERVER_START_TIMEOUT = Duration.ofMinutes(15);
    protected static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    protected static final Duration BLOCK_STATE_TIMEOUT = Duration.ofSeconds(180);

    protected static PaperServer server;
    private static Path serverDirectory;
    private static java.util.Map<String, String> integrationTestSystemProperties;
    private static boolean serverStarting;

    @BeforeAll
    static void startPaperServer() throws Exception {
        if (server != null) {
            return;
        }
        serverStarting = true;
        try {
            Path pluginJar = requiredPathProperty("customcontent.pluginJar");
            Path paperJar = requiredPathProperty("customcontent.paperJar");
            serverDirectory = Files.createTempDirectory("customcontent-paper-it-");
            PaperServer.prepareServerDirectory(serverDirectory, pluginJar);
            java.util.Map<String, String> properties = integrationTestSystemProperties != null ? integrationTestSystemProperties : new java.util.HashMap<>();
        server = PaperServer.start(serverDirectory, paperJar, properties);
        server.awaitOutput(line -> line.contains("Done preparing level"), SERVER_START_TIMEOUT);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (server != null) {
                        server.close();
                    }
                    if (serverDirectory != null) {
                        PaperServer.deleteRecursively(serverDirectory);
                    }
                } catch (Exception exception) {
                    // shutdown best-effort
                }
            }));
        } finally {
            serverStarting = false;
        }
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

    @BeforeEach
    void resetOutput() {
        if (server != null) {
            server.clearOutput();
        }
    }

    @AfterEach
    void cleanupServerState() throws Exception {
        if (server == null || serverStarting) {
            return;
        }
        try {
            sendCommand("debugquery 0 0 0 world");
        } catch (Exception exception) {
            // best-effort cleanup ping
        }
    }

    protected static void setIntegrationTestSystemProperties(java.util.Map<String, String> properties) {
        integrationTestSystemProperties = properties;
    }

    protected static Path serverDirectory() {
        return serverDirectory;
    }

    protected static PaperServer server() {
        return server;
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
        awaitOutput(line -> line.contains("debugplace ok: " + blockId), COMMAND_TIMEOUT);
    }

    protected static void mineBlock(String toolId, WorldPosition position) throws Exception {
        sendCommand("debugmine " + toolId + " " + position.x() + " " + position.y() + " " + position.z() + " " + position.worldName());
        awaitOutput(line -> line.contains("debugmine started: " + toolId), COMMAND_TIMEOUT);
    }

    protected static void awaitBlockState(WorldPosition position, String expectedNumericId, String expectedMaterial, Duration timeout) throws InterruptedException, java.io.IOException {
        String target = "x=" + position.x() + " y=" + position.y() + " z=" + position.z()
                + " numericId=" + expectedNumericId + " material=" + expectedMaterial;

        server.clearOutput();
        sendCommand("debugquery " + position.x() + " " + position.y() + " " + position.z() + " " + position.worldName());
        Thread.sleep(300);
        if (server.outputContains(target)) {
            return;
        }

        Duration effective = timeout == null ? BLOCK_STATE_TIMEOUT : timeout;
        long deadline = System.nanoTime() + effective.toNanos();
        long waitMillis = 300;
        while (System.nanoTime() < deadline) {
            Thread.sleep(waitMillis);
            waitMillis = Math.min(waitMillis + 100, 1000);

            server.clearOutput();
            sendCommand("debugquery " + position.x() + " " + position.y() + " " + position.z() + " " + position.worldName());
            Thread.sleep(300);
            if (server.outputContains(target)) {
                return;
            }
        }
        throw new AssertionError("Timed out waiting for block state.%n%s".formatted(fullOutput()));
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