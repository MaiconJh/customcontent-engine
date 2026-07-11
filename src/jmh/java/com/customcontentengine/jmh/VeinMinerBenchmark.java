package com.customcontentengine.jmh;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Vein Miner BFS – self-contained version.
 * Duplicates the minimal logic from the spike to avoid visibility issues.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class VeinMinerBenchmark {

    @Param({"10", "25", "50", "64", "100", "150", "200"})
    public int veinSize;

    @Param({"false", "true"})
    public boolean allAdjacent;

    private static final int MAX_BLOCKS = 64;
    private static final int MAX_DEPTH = 20;

    // Pre-computed offsets for all-adjacent (26 directions)
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

    // ========== INTERNAL DATA STRUCTURES (copied from spike) ==========

    private record Position(int x, int y, int z, short blockType) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Position p = (Position) o;
            return x == p.x && y == p.y && z == p.z;
        }
        @Override
        public int hashCode() {
            return 31 * 31 * x + 31 * y + z;
        }
    }

    private record VeinScenario(List<Position> positions, Position origin, Set<Position> customBlockSet) {}

    private record VeinNode(Position position, int depth) {}

    // ========== SCENARIO CREATION (copied from spike) ==========

    private VeinScenario createVeinScenario(int size) {
        Set<Position> customBlocks = new HashSet<>(size * 2);
        List<Position> positions = new ArrayList<>(size);
        Position origin = new Position(0, 64, 0, (short) 1);
        customBlocks.add(origin);
        positions.add(origin);
        for (int i = 1; i < size; i++) {
            Position adjacent = new Position(origin.x + i, origin.y, origin.z, origin.blockType);
            customBlocks.add(adjacent);
            positions.add(adjacent);
        }
        return new VeinScenario(positions, origin, customBlocks);
    }

    // ========== BFS IMPLEMENTATIONS (copied from spike) ==========

    private List<Position> faceAdjacent(Position pos) {
        List<Position> adj = new ArrayList<>(6);
        adj.add(new Position(pos.x + 1, pos.y, pos.z, pos.blockType));
        adj.add(new Position(pos.x - 1, pos.y, pos.z, pos.blockType));
        adj.add(new Position(pos.x, pos.y + 1, pos.z, pos.blockType));
        adj.add(new Position(pos.x, pos.y - 1, pos.z, pos.blockType));
        adj.add(new Position(pos.x, pos.y, pos.z + 1, pos.blockType));
        adj.add(new Position(pos.x, pos.y, pos.z - 1, pos.blockType));
        return adj;
    }

    private List<Position> allAdjacent(Position pos) {
        List<Position> adj = new ArrayList<>(26);
        for (int[] offset : ALL_OFFSETS) {
            adj.add(new Position(pos.x + offset[0], pos.y + offset[1], pos.z + offset[2], pos.blockType));
        }
        return adj;
    }

    private int runBfs(VeinScenario scenario, int maxBlocks, int maxDepth, boolean allAdj) {
        Set<Position> visited = new HashSet<>(maxBlocks * 2);
        Deque<VeinNode> queue = new ArrayDeque<>(maxBlocks);
        int processed = 0;
        visited.add(scenario.origin);
        queue.add(new VeinNode(scenario.origin, 0));

        while (!queue.isEmpty() && processed < maxBlocks) {
            VeinNode node = queue.poll();
            if (node.depth > maxDepth) continue;
            if (scenario.customBlockSet.contains(node.position)) {
                processed++;
            }
            List<Position> neighbors = allAdj ? allAdjacent(node.position) : faceAdjacent(node.position);
            for (Position neighbor : neighbors) {
                if (visited.add(neighbor)) {
                    queue.add(new VeinNode(neighbor, node.depth + 1));
                }
            }
        }
        return processed;
    }

    // ========== BENCHMARK METHODS ==========

    /**
     * Benchmark: creates scenario fresh for each iteration.
     */
    @Benchmark
    public int bfsFresh() {
        VeinScenario scenario = createVeinScenario(veinSize);
        return runBfs(scenario, MAX_BLOCKS, MAX_DEPTH, allAdjacent);
    }

    /**
     * Benchmark with cached scenario (created once per fork).
     * More representative of real-world usage.
     */
    @State(Scope.Benchmark)
    public static class CachedScenarioState {
        @Param({"10", "25", "50", "64", "100", "150", "200"})
        public int veinSize;

        @Param({"false", "true"})
        public boolean allAdjacent;

        private VeinScenario scenario;

        @Setup(Level.Trial)
        public void setup() {
            // Use the outer class's method (or duplicate logic)
            VeinMinerBenchmark outer = new VeinMinerBenchmark();
            scenario = outer.createVeinScenario(veinSize);
        }

        @Benchmark
        public int bfsCached() {
            return new VeinMinerBenchmark().runBfs(scenario, MAX_BLOCKS, MAX_DEPTH, allAdjacent);
        }
    }
}