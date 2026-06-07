package com.customcontentengine.spike;

import com.customcontentengine.adapter.persistence.PdcBlockCodec;
import com.customcontentengine.adapter.persistence.PdcBlockCodec.PdcBlockEntry;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BinaryPdcPerformanceSpike {
    private static final int[] ENTRY_COUNTS = {256, 512, 1024};
    private static final int WARMUP_ITERATIONS = 2_000;
    private static final int MEASUREMENT_ITERATIONS = 8_000;
    private static volatile long sink;

    private BinaryPdcPerformanceSpike() {
    }

    public static void main(String[] args) throws IOException {
        Path reportPath = args.length == 0
                ? Path.of("build/reports/spikes/001-binary-pdc-performance-results.md")
                : Path.of(args[0]);
        PdcBlockCodec codec = new PdcBlockCodec();
        List<Result> results = new ArrayList<>();

        for (int entryCount : ENTRY_COUNTS) {
            Scenario scenario = scenario(codec, entryCount);
            results.add(measure("encode", entryCount, () -> consume(codec.encode(scenario.entries()))));
            results.add(measure("decode", entryCount, () -> consume(codec.decode(scenario.encoded()).entries().size())));
            results.add(measure("lookup/findNumericId", entryCount, () -> consume(
                    codec.findNumericId(scenario.encoded(), scenario.existingPackedPosition()).orElseThrow())));
            results.add(measure("add/upsert", entryCount, () -> consume(
                    upsert(codec, scenario.encoded(), scenario.missingPackedPosition(), (short) 32000).length)));
            results.add(measure("remove", entryCount, () -> consume(
                    codec.remove(scenario.encoded(), scenario.existingPackedPosition()).length)));
        }

        String report = report(results);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private static Scenario scenario(PdcBlockCodec codec, int entryCount) {
        List<PdcBlockEntry> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            entries.add(new PdcBlockEntry(packedPosition(codec, index), (short) (index + 1)));
        }
        short existingPackedPosition = packedPosition(codec, entryCount - 1);
        short missingPackedPosition = packedPosition(codec, entryCount);
        return new Scenario(List.copyOf(entries), codec.encode(entries), existingPackedPosition, missingPackedPosition);
    }

    private static short packedPosition(PdcBlockCodec codec, int index) {
        int x = index & 0xF;
        int z = (index >> 4) & 0xF;
        int y = (index >> 8) & 0xFF;
        return codec.packRelativePosition(x, y, z);
    }

    private static byte[] upsert(PdcBlockCodec codec, byte[] data, short packedPosition, short numericId) {
        List<PdcBlockEntry> decodedEntries = codec.decode(data).entries();
        ArrayList<PdcBlockEntry> entries = new ArrayList<>(decodedEntries);
        for (int index = 0; index < entries.size(); index++) {
            PdcBlockEntry entry = entries.get(index);
            if (entry.packedPosition() == packedPosition) {
                entries.set(index, new PdcBlockEntry(packedPosition, numericId));
                return codec.encode(entries);
            }
        }
        entries.add(new PdcBlockEntry(packedPosition, numericId));
        return codec.encode(entries);
    }

    private static Result measure(String operation, int entryCount, Operation measuredOperation) {
        for (int index = 0; index < WARMUP_ITERATIONS; index++) {
            measuredOperation.run();
        }

        long allocationBefore = allocatedBytes();
        long timeBefore = System.nanoTime();
        for (int index = 0; index < MEASUREMENT_ITERATIONS; index++) {
            measuredOperation.run();
        }
        long elapsedNanos = System.nanoTime() - timeBefore;
        long allocationAfter = allocatedBytes();
        Optional<Long> allocatedBytes = allocationBefore >= 0 && allocationAfter >= 0
                ? Optional.of(allocationAfter - allocationBefore)
                : Optional.empty();
        return new Result(
                operation,
                entryCount,
                elapsedNanos / (double) MEASUREMENT_ITERATIONS / 1_000.0,
                allocatedBytes.map(bytes -> bytes / (double) MEASUREMENT_ITERATIONS));
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean rawBean = ManagementFactory.getThreadMXBean();
        if (rawBean instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported()) {
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1;
    }

    private static void consume(byte[] value) {
        sink += value.length;
    }

    private static void consume(short value) {
        sink += value;
    }

    private static void consume(int value) {
        sink += value;
    }

    private static String report(List<Result> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Binary PDC Performance Raw Results").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Date: ").append(LocalDate.now()).append(System.lineSeparator());
        builder.append("Java: ").append(System.getProperty("java.version")).append(System.lineSeparator());
        builder.append("OS: ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append(" ")
                .append(System.getProperty("os.arch")).append(System.lineSeparator());
        builder.append("Warmup iterations per operation: ").append(WARMUP_ITERATIONS).append(System.lineSeparator());
        builder.append("Measured iterations per operation: ").append(MEASUREMENT_ITERATIONS).append(System.lineSeparator());
        builder.append("Sink: ").append(sink).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("| Entries | Operation | Average microseconds/op | Approx allocated bytes/op |").append(System.lineSeparator());
        builder.append("| ---: | --- | ---: | ---: |").append(System.lineSeparator());
        for (Result result : results) {
            builder.append("| ")
                    .append(result.entryCount())
                    .append(" | ")
                    .append(result.operation())
                    .append(" | ")
                    .append("%.3f".formatted(result.averageMicros()))
                    .append(" | ")
                    .append(result.allocatedBytesPerOperation()
                            .map(bytes -> "%.1f".formatted(bytes))
                            .orElse("unavailable"))
                    .append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private record Scenario(
            List<PdcBlockEntry> entries,
            byte[] encoded,
            short existingPackedPosition,
            short missingPackedPosition) {
    }

    private record Result(
            String operation,
            int entryCount,
            double averageMicros,
            Optional<Double> allocatedBytesPerOperation) {
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }
}
