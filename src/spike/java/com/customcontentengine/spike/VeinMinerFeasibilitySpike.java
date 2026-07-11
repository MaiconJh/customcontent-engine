package com.customcontentengine.spike;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class VeinMinerFeasibilitySpike {

    // ========== CONFIGURAÇÃO ==========
    private static final String MODE = System.getenv().getOrDefault("MODE", "medium");

    private static final boolean FAST_MODE = "fast".equalsIgnoreCase(MODE);
    private static final boolean COMPLETE_MODE = "complete".equalsIgnoreCase(MODE);

    private static final int[] VEIN_SIZES = FAST_MODE
            ? new int[]{64}
            : new int[]{10, 25, 50, 64, 100, 150, 200};

    private static final int WARMUP_ITERATIONS = FAST_MODE ? 10
            : COMPLETE_MODE ? 2_000
            : 200;

    private static final int MEASUREMENT_ITERATIONS = FAST_MODE ? 20
            : COMPLETE_MODE ? 8_000
            : 500;

    // ========== LOGGING ==========
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int LOG_INTERVAL_PERCENT = 10; // Log a cada 10%

    // ========== CACHE DE OFFSETS PARA ALL_ADJACENT ==========
    private static final int[][] ALL_OFFSETS = new int[26][3];
    static {
        int idx = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    ALL_OFFSETS[idx++] = new int[]{dx, dy, dz};
                }
            }
        }
    }

    private static volatile long sink;

    private VeinMinerFeasibilitySpike() {
    }

    public static void main(String[] args) throws Exception {
        Instant overallStart = Instant.now();
        logInfo("========================================");
        logInfo("Spike started at " + LocalDateTime.now());
        logInfo("========================================");
        logInfo("MODE = " + MODE);
        logInfo("VEIN_SIZES = " + java.util.Arrays.toString(VEIN_SIZES));
        logInfo("WARMUP_ITERATIONS = " + WARMUP_ITERATIONS);
        logInfo("MEASUREMENT_ITERATIONS = " + MEASUREMENT_ITERATIONS);
        logInfo("LOG_INTERVAL = " + LOG_INTERVAL_PERCENT + "%");
        logInfo("========================================");
        logInfo("");

        Path reportPath = args.length == 0
                ? Path.of("build/reports/spikes/005-vein-miner-feasibility-results.md")
                : Path.of(args[0]);

        List<Result> results = new ArrayList<>();
        int totalOperations = VEIN_SIZES.length * 3;
        int current = 0;

        for (int veinSize : VEIN_SIZES) {
            logInfo("--- Creating scenario for vein size " + veinSize + " ---");
            VeinScenario scenario = createVeinScenario(veinSize);
            logInfo("Scenario created with " + scenario.positions().size() + " positions.");

            current++;
            logInfo("[" + current + "/" + totalOperations + "] Measuring bfs-hashset (veinSize=" + veinSize + ")...");
            results.add(measureWithProgress("bfs-hashset", veinSize, () -> runVeinMinerBfs(
                    scenario.customBlockSet(), scenario.origin, 64, 20)));

            current++;
            logInfo("[" + current + "/" + totalOperations + "] Measuring bfs-arraylist (veinSize=" + veinSize + ")...");
            results.add(measureWithProgress("bfs-arraylist", veinSize, () -> runVeinMinerBfsArrayList(
                    scenario.customBlockSet(), scenario.origin, 64, 20)));

            current++;
            logInfo("[" + current + "/" + totalOperations + "] Measuring bfs-hashset-alladjacent (veinSize=" + veinSize + ")...");
            results.add(measureWithProgress("bfs-hashset-alladjacent", veinSize, () -> runVeinMinerBfsAllAdjacent(
                    scenario.customBlockSet(), scenario.origin, 64, 20)));
        }

        logInfo("");
        logInfo("========================================");
        logInfo("All measurements done. Generating report...");
        String report = report(results);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report);
        logInfo("Report written to " + reportPath.toAbsolutePath());
        logInfo("");
        logInfo(report);
        logInfo("");
        logInfo("========================================");
        logInfo("Spike finished at " + LocalDateTime.now());
        logInfo("Total elapsed time: " + formatDuration(Duration.between(overallStart, Instant.now())));
        logInfo("========================================");
    }

    // ==================== LOGGING UTILITIES ====================

    private static void logInfo(String message) {
        System.out.printf("[%s] [INFO] %s%n",
                LocalDateTime.now().format(TIME_FORMATTER), message);
    }

    private static void logDebug(String message) {
        System.out.printf("[%s] [DEBUG] %s%n",
                LocalDateTime.now().format(TIME_FORMATTER), message);
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long millis = duration.toMillisPart();
        if (seconds < 60) {
            return seconds + "s " + millis + "ms";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m " + seconds + "s";
    }

    private static String estimateETA(Duration elapsed, int done, int total) {
        if (done == 0) return "calculating...";
        long avgNanos = elapsed.toNanos() / done;
        long remainingNanos = avgNanos * (total - done);
        return formatDuration(Duration.ofNanos(remainingNanos));
    }

    // ==================== MEASUREMENT WITH PROGRESS LOGGING ====================

    private static Result measureWithProgress(String operation, int veinSize, Operation measuredOperation) {
        Instant taskStart = Instant.now();

        logInfo("  Warming up (" + WARMUP_ITERATIONS + " iterations)...");
        runWithProgress(operation + ":warmup", WARMUP_ITERATIONS, measuredOperation);

        logInfo("  Measuring (" + MEASUREMENT_ITERATIONS + " iterations)...");
        long allocationBefore = allocatedBytes();
        long timeBefore = System.nanoTime();

        runWithProgress(operation + ":measure", MEASUREMENT_ITERATIONS, measuredOperation);

        long elapsedNanos = System.nanoTime() - timeBefore;
        long allocationAfter = allocatedBytes();

        double avgMicros = elapsedNanos / (double) MEASUREMENT_ITERATIONS / 1_000.0;
        Optional<Double> avgBytes = (allocationAfter >= 0 && allocationBefore >= 0)
                ? Optional.of((allocationAfter - allocationBefore) / (double) MEASUREMENT_ITERATIONS)
                : Optional.empty();

        Duration elapsed = Duration.between(taskStart, Instant.now());
        logInfo("  ✅ Done: " + String.format("%.3f", avgMicros) + " μs/op" +
                avgBytes.map(b -> ", " + String.format("%.1f", b) + " bytes/op").orElse("") +
                " (total elapsed: " + formatDuration(elapsed) + ")");
        logInfo("");

        return new Result(operation, veinSize, avgMicros, avgBytes);
    }

    private static void runWithProgress(String label, int totalIterations, Operation operation) {
        if (totalIterations <= 0) return;
        int logInterval = Math.max(1, totalIterations / (100 / LOG_INTERVAL_PERCENT));
        Instant start = Instant.now();

        for (int i = 0; i < totalIterations; i++) {
            operation.run();
            if ((i + 1) % logInterval == 0 || (i + 1) == totalIterations) {
                int percent = (int) (((i + 1) * 100.0) / totalIterations);
                Duration elapsed = Duration.between(start, Instant.now());
                double avgMsPerOp = elapsed.toNanos() / (double) (i + 1) / 1_000_000.0;
                String eta = estimateETA(elapsed, i + 1, totalIterations);
                logDebug("    " + label + " progress: " + percent + "% (" + (i + 1) + "/" + totalIterations +
                        ") - elapsed: " + formatDuration(elapsed) +
                        ", avg: " + String.format("%.2f", avgMsPerOp) + " ms/op" +
                        ", ETA: " + eta);
            }
        }
    }

    // ==================== BFS IMPLEMENTATIONS ====================

    private record VeinScenario(
            List<Position> positions,
            Position origin,
            short blockType,
            Set<Position> customBlockSet) {
    }

    private static VeinScenario createVeinScenario(int veinSize) {
        Set<Position> customBlocks = new HashSet<>(veinSize * 2);
        List<Position> positions = new ArrayList<>(veinSize);
        Position origin = new Position(0, 64, 0, (short) 1);

        customBlocks.add(origin);
        positions.add(origin);

        for (int i = 1; i < veinSize; i++) {
            Position adjacent = origin.withOffset(i, 0, 0);
            customBlocks.add(adjacent);
            positions.add(adjacent);
        }

        return new VeinScenario(positions, origin, (short) 1, customBlocks);
    }

    private static int runVeinMinerBfs(
            Set<Position> customBlockSet,
            Position origin,
            int maxBlocks,
            int maxDepth) {
        Set<Position> visited = new HashSet<>(maxBlocks * 2);
        Deque<VeinNode> queue = new ArrayDeque<>(maxBlocks);
        int processed = 0;

        visited.add(origin);
        queue.add(new VeinNode(origin, 0));

        while (!queue.isEmpty() && processed < maxBlocks) {
            VeinNode node = queue.poll();
            if (node.depth() > maxDepth) continue;

            if (customBlockSet.contains(node.position())) {
                processed++;
            }

            for (Position neighbor : faceAdjacent(node.position())) {
                if (visited.add(neighbor)) {
                    queue.add(new VeinNode(neighbor, node.depth() + 1));
                }
            }
        }

        consume(processed);
        return processed;
    }

    private static int runVeinMinerBfsArrayList(
            Set<Position> customBlockSet,
            Position origin,
            int maxBlocks,
            int maxDepth) {
        List<Position> visited = new ArrayList<>(maxBlocks);
        Deque<VeinNode> queue = new ArrayDeque<>(maxBlocks);
        int processed = 0;

        visited.add(origin);
        queue.add(new VeinNode(origin, 0));

        while (!queue.isEmpty() && processed < maxBlocks) {
            VeinNode node = queue.poll();
            if (node.depth() > maxDepth) continue;

            if (customBlockSet.contains(node.position())) {
                processed++;
            }

            for (Position neighbor : faceAdjacent(node.position())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(new VeinNode(neighbor, node.depth() + 1));
                }
            }
        }

        consume(processed);
        return processed;
    }

    private static int runVeinMinerBfsAllAdjacent(
            Set<Position> customBlockSet,
            Position origin,
            int maxBlocks,
            int maxDepth) {
        Set<Position> visited = new HashSet<>(maxBlocks * 2);
        Deque<VeinNode> queue = new ArrayDeque<>(maxBlocks);
        int processed = 0;

        visited.add(origin);
        queue.add(new VeinNode(origin, 0));

        while (!queue.isEmpty() && processed < maxBlocks) {
            VeinNode node = queue.poll();
            if (node.depth() > maxDepth) continue;

            if (customBlockSet.contains(node.position())) {
                processed++;
            }

            for (Position neighbor : allAdjacent(node.position())) {
                if (visited.add(neighbor)) {
                    queue.add(new VeinNode(neighbor, node.depth() + 1));
                }
            }
        }

        consume(processed);
        return processed;
    }

    private record VeinNode(Position position, int depth) {
    }

    private static List<Position> faceAdjacent(Position pos) {
        List<Position> adjacent = new ArrayList<>(6);
        adjacent.add(new Position(pos.x() + 1, pos.y(), pos.z(), pos.blockType()));
        adjacent.add(new Position(pos.x() - 1, pos.y(), pos.z(), pos.blockType()));
        adjacent.add(new Position(pos.x(), pos.y() + 1, pos.z(), pos.blockType()));
        adjacent.add(new Position(pos.x(), pos.y() - 1, pos.z(), pos.blockType()));
        adjacent.add(new Position(pos.x(), pos.y(), pos.z() + 1, pos.blockType()));
        adjacent.add(new Position(pos.x(), pos.y(), pos.z() - 1, pos.blockType()));
        return adjacent;
    }

    private static List<Position> allAdjacent(Position pos) {
        List<Position> adjacent = new ArrayList<>(26);
        for (int[] offset : ALL_OFFSETS) {
            adjacent.add(new Position(
                    pos.x() + offset[0],
                    pos.y() + offset[1],
                    pos.z() + offset[2],
                    pos.blockType()
            ));
        }
        return adjacent;
    }

    private record Position(int x, int y, int z, short blockType) {
        Position withOffset(int dx, int dy, int dz) {
            return new Position(x + dx, y + dy, z + dz, blockType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Position position = (Position) o;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override
        public int hashCode() {
            return 31 * 31 * x + 31 * y + z;
        }

        @Override
        public String toString() {
            return x + "," + y + "," + z;
        }
    }

    // ==================== UTILITIES ====================

    private static long allocatedBytes() {
        var rawBean = ManagementFactory.getThreadMXBean();
        if (rawBean instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported()) {
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1;
    }

    private static void consume(int value) {
        sink += value;
    }

    private static String report(List<Result> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Spike 5 - Vein Miner Feasibility Raw Results").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Date: ").append(LocalDate.now()).append(System.lineSeparator());
        builder.append("Java: ").append(System.getProperty("java.version")).append(System.lineSeparator());
        builder.append("OS: ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append(" ")
                .append(System.getProperty("os.arch")).append(System.lineSeparator());
        builder.append("Warmup iterations per operation: ").append(WARMUP_ITERATIONS).append(System.lineSeparator());
        builder.append("Measured iterations per operation: ").append(MEASUREMENT_ITERATIONS).append(System.lineSeparator());
        builder.append("MODE: ").append(MODE).append(System.lineSeparator());
        builder.append("Sink: ").append(sink).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("| Vein Size | Operation | Average microseconds/op | Approx allocated bytes/op |").append(System.lineSeparator());
        builder.append("| ---: | --- | ---: | ---: |").append(System.lineSeparator());
        for (Result result : results) {
            builder.append("| ")
                    .append(result.veinSize())
                    .append(" | ")
                    .append(result.operation())
                    .append(" | ")
                    .append(String.format("%.3f", result.averageMicros()))
                    .append(" | ")
                    .append(result.allocatedBytesPerOperation().map(bytes -> String.format("%.1f", bytes)).orElse("unavailable"))
                    .append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private record Result(String operation, int veinSize, double averageMicros, Optional<Double> allocatedBytesPerOperation) {
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }
}