package com.customcontentengine.spike;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class VeinMinerFeasibilitySpike {

    // ========== CONFIGURAÇÃO ==========
    private static final boolean FAST_MODE = true;  // Altere para false para execução completa

    private static final int[] VEIN_SIZES = FAST_MODE
            ? new int[]{64}
            : new int[]{10, 25, 50, 64, 100, 150, 200};

    private static final int WARMUP_ITERATIONS = FAST_MODE ? 10 : 2_000;
    private static final int MEASUREMENT_ITERATIONS = FAST_MODE ? 20 : 8_000;

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
        System.out.println("Spike started at " + LocalDateTime.now());
        System.out.println("FAST_MODE = " + FAST_MODE);
        System.out.println("VEIN_SIZES = " + java.util.Arrays.toString(VEIN_SIZES));
        System.out.println("WARMUP_ITERATIONS = " + WARMUP_ITERATIONS);
        System.out.println("MEASUREMENT_ITERATIONS = " + MEASUREMENT_ITERATIONS);

        Path reportPath = args.length == 0
                ? Path.of("build/reports/spikes/005-vein-miner-feasibility-results.md")
                : Path.of(args[0]);

        List<Result> results = new ArrayList<>();
        int totalOperations = VEIN_SIZES.length * 3;
        int current = 0;

        for (int veinSize : VEIN_SIZES) {
            System.out.println("--- Creating scenario for vein size " + veinSize + " ---");
            VeinScenario scenario = createVeinScenario(veinSize);
            System.out.println("Scenario created with " + scenario.positions().size() + " positions.");

            current++;
            System.out.println("[" + current + "/" + totalOperations + "] Measuring bfs-hashset...");
            results.add(measure("bfs-hashset", veinSize, () -> runVeinMinerBfs(
                    scenario.customBlockSet(), scenario.origin, 64, 20)));

            current++;
            System.out.println("[" + current + "/" + totalOperations + "] Measuring bfs-arraylist...");
            results.add(measure("bfs-arraylist", veinSize, () -> runVeinMinerBfsArrayList(
                    scenario.customBlockSet(), scenario.origin, 64, 20)));

            current++;
            System.out.println("[" + current + "/" + totalOperations + "] Measuring bfs-hashset-alladjacent...");
            results.add(measure("bfs-hashset-alladjacent", veinSize, () -> runVeinMinerBfsAllAdjacent(
                    scenario.customBlockSet(), scenario.origin, 64, 20)));
        }

        System.out.println("All measurements done. Generating report...");
        String report = report(results);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report);
        System.out.println("Report written to " + reportPath.toAbsolutePath());
        System.out.println(report);
        System.out.println("Spike finished at " + LocalDateTime.now());
    }

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

    private static Result measure(String operation, int veinSize, Operation measuredOperation) {
        System.out.println("  Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            measuredOperation.run();
        }
        System.out.println("  Measuring...");
        long allocationBefore = allocatedBytes();
        long timeBefore = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            measuredOperation.run();
        }
        long elapsedNanos = System.nanoTime() - timeBefore;
        long allocationAfter = allocatedBytes();

        double avgMicros = elapsedNanos / (double) MEASUREMENT_ITERATIONS / 1_000.0;
        Optional<Double> avgBytes = (allocationAfter >= 0 && allocationBefore >= 0)
                ? Optional.of((allocationAfter - allocationBefore) / (double) MEASUREMENT_ITERATIONS)
                : Optional.empty();

        System.out.println("  Done: " + String.format("%.3f", avgMicros) + " μs/op" +
                avgBytes.map(b -> ", " + String.format("%.1f", b) + " bytes/op").orElse(""));
        return new Result(operation, veinSize, avgMicros, avgBytes);
    }

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
        builder.append("FAST_MODE: ").append(FAST_MODE).append(System.lineSeparator());
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