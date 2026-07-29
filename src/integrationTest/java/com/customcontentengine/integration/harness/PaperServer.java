package com.customcontentengine.integration.harness;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Reusable harness that drives a real Paper server process for integration tests.
 *
 * <p>The server runs in a separate JVM (started via {@link ProcessBuilder}); all output is
 * captured line by line so tests can await conditions or assert on emitted log lines. This keeps
 * the integration tests honest: they validate the plugin against the actual Paper runtime,
 * including PDC chunk serialization, scheduler tick alignment, and Bukkit event semantics.</p>
 */
public final class PaperServer implements AutoCloseable {
    private final Process process;
    private final BufferedWriter input;
    private final List<String> outputLines = new CopyOnWriteArrayList<>();
    private final Thread outputReader;

    private PaperServer(Process process) {
        this.process = process;
        this.input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.outputReader = new Thread(this::readOutput, "paper-integration-output-reader");
        this.outputReader.setDaemon(true);
        this.outputReader.start();
    }

    /**
     * Prepares a server directory with the required {@code eula.txt}, {@code server.properties}
     * (flat world, no structures, small view distance for fast startup) and copies the plugin jar
     * into the {@code plugins} folder.
     */
    public static void prepareServerDirectory(Path serverDirectory, Path pluginJar) throws IOException {
        Files.writeString(
                serverDirectory.resolve("eula.txt"),
                "eula=true%n".formatted(),
                StandardCharsets.UTF_8);
        Files.writeString(
                serverDirectory.resolve("server.properties"),
                """
                online-mode=false
                server-port=%d
                query.port=%d
                enable-query=false
                spawn-protection=0
                view-distance=2
                simulation-distance=2
                generate-structures=false
                level-type=minecraft:flat
                """.formatted(freePort(), freePort()),
                StandardCharsets.UTF_8);
        Path pluginsDirectory = Files.createDirectories(serverDirectory.resolve("plugins"));
        Files.copy(pluginJar, pluginsDirectory.resolve("CustomContentEngine.jar"));
    }

    public static PaperServer start(Path serverDirectory, Path paperJar) throws IOException {
        return start(serverDirectory, paperJar, java.util.Map.of());
    }

    public static PaperServer start(Path serverDirectory, Path paperJar, java.util.Map<String, String> systemProperties) throws IOException {
        java.util.List<String> commands = new java.util.ArrayList<>();
        commands.add(javaExecutable());
        commands.add("-Xms512M");
        commands.add("-Xmx1G");
        commands.add("-XX:+TieredCompilation");
        commands.add("-XX:TieredStopAtLevel=1");
        commands.add("-jar");
        commands.add(paperJar.toAbsolutePath().toString());
        commands.add("--nogui");
        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.directory(serverDirectory.toFile());
        builder.redirectErrorStream(true);
        java.util.Map<String, String> environment = builder.environment();
        environment.putAll(systemProperties);
        return new PaperServer(builder.start());
    }

    public void awaitOutput(Predicate<String> predicate, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int lastIndex = 0;
        long waitMillis = 100;
        while (System.nanoTime() < deadline) {
            int currentIndex = outputLines.size();
            for (int index = lastIndex; index < currentIndex; index++) {
                if (predicate.test(outputLines.get(index))) {
                    return;
                }
            }
            lastIndex = currentIndex;
            if (!process.isAlive()) {
                throw new AssertionError("Paper server exited before expected output.%n%s".formatted(fullOutput()));
            }
            Thread.sleep(waitMillis);
            if (waitMillis < 500) waitMillis += 50;
        }
        throw new AssertionError("Timed out waiting for Paper server output.%n%s".formatted(fullOutput()));
    }

    public void sendCommand(String command) throws IOException {
        input.write(command);
        input.newLine();
        input.flush();
    }

    public boolean outputContains(String text) {
        return outputLines.stream().anyMatch(line -> line.contains(text));
    }

    public int outputLineCount() {
        return outputLines.size();
    }

    public String outputLine(int index) {
        if (index < 0 || index >= outputLines.size()) {
            return null;
        }
        return outputLines.get(index);
    }

    public void clearOutput() {
        outputLines.clear();
    }

    public String fullOutput() {
        return String.join(System.lineSeparator(), outputLines);
    }

    private void readOutput() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
                System.out.println("[paper-it] " + line);
            }
        } catch (IOException exception) {
            outputLines.add("Output reader failed: " + exception.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        if (process.isAlive()) {
            sendCommand("stop");
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(15, TimeUnit.SECONDS);
            }
        }
    }

    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            List<Path> orderedPaths = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path current : orderedPaths) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}