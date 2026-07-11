package com.customcontentengine.builtin.mechanic;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ActorState;
import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.internalapi.mechanic.capability.MechanicArguments;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class VeinMinerMechanic implements Mechanic {
    public static final MechanicId ID = new MechanicId("vein_miner");
    private static final int DEFAULT_MAX_BLOCKS = 64;
    private static final int DEFAULT_MAX_DEPTH = 20;
    private static final int ABSOLUTE_MAX_BLOCKS = 512;
    private static final int HARD_MAX_DEPTH = 64;
    private static final int ALL_ADJACENT_MAX_BLOCKS = 32;
    private static final int ALL_ADJACENT_MAX_DEPTH = 10;

    private static final MechanicDescriptor DESCRIPTOR = new MechanicDescriptor(ID, Set.of(
            Capability.BLOCK_QUERY,
            Capability.BLOCK_MUTATION,
            Capability.BUDGET_VIEW,
            Capability.COOLDOWN_VIEW,
            Capability.DROP_SINK,
            Capability.EXECUTION_ORIGIN
    ), false, Set.of(
            Capability.ENCHANTMENT_VIEW,
            Capability.MECHANIC_ARGUMENTS,
            Capability.ACTOR_STATE));

    private final int defaultMaxBlocks;
    private final int defaultMaxDepth;
    private final boolean defaultRespectFortune;
    private final boolean defaultRespectSilkTouch;

    public VeinMinerMechanic() {
        this(DEFAULT_MAX_BLOCKS, DEFAULT_MAX_DEPTH);
    }

    public VeinMinerMechanic(int defaultMaxBlocks, int defaultMaxDepth) {
        this(defaultMaxBlocks, defaultMaxDepth, true, true);
    }

    public VeinMinerMechanic(int defaultMaxBlocks, int defaultMaxDepth, boolean defaultRespectFortune,
            boolean defaultRespectSilkTouch) {
        if (defaultMaxBlocks <= 0) {
            throw new IllegalArgumentException("defaultMaxBlocks must be positive");
        }
        if (defaultMaxDepth <= 0) {
            throw new IllegalArgumentException("defaultMaxDepth must be positive");
        }
        this.defaultMaxBlocks = defaultMaxBlocks;
        this.defaultMaxDepth = defaultMaxDepth;
        this.defaultRespectFortune = defaultRespectFortune;
        this.defaultRespectSilkTouch = defaultRespectSilkTouch;
    }

    @Override
    public MechanicDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public MechanicResult execute(MechanicContext context) {
        Objects.requireNonNull(context, "context");
        BlockQuery blockQuery;
        BlockMutation blockMutation;
        BudgetView budgetView;
        CooldownView cooldownView;
        DropSink dropSink;
        ExecutionOrigin executionOrigin;

        try {
            blockQuery = context.require(BlockQuery.class);
            blockMutation = context.require(BlockMutation.class);
            budgetView = context.require(BudgetView.class);
            cooldownView = context.require(CooldownView.class);
            dropSink = context.require(DropSink.class);
            executionOrigin = context.require(ExecutionOrigin.class);
        } catch (IllegalArgumentException exception) {
            return new MechanicResult.Rejected(exception.getMessage());
        }

        Optional<EnchantmentView> enchantmentView = context.optional(EnchantmentView.class);
        Optional<MechanicArguments> arguments = context.optional(MechanicArguments.class);

        if (!cooldownView.canExecute()) {
            return new MechanicResult.Rejected("Cooldown rejected vein_miner");
        }

        boolean requireSneak = resolveBoolean(arguments, "require_sneak", false);
        Optional<ActorState> actorState = context.optional(ActorState.class);
        if (requireSneak) {
            if (actorState.isEmpty()) {
                return new MechanicResult.Rejected("require_sneak enabled but actor state unavailable");
            }
            if (!actorState.get().isSneaking()) {
                return new MechanicResult.Rejected("require_sneak enabled and actor is not sneaking");
            }
        }

        WorldPosition origin = Objects.requireNonNull(executionOrigin.origin(), "origin");
        Optional<Short> originBlockId = blockQuery.findCustomBlockNumericId(origin);

        if (originBlockId.isEmpty()) {
            return new MechanicResult.Rejected("Origin block is not a custom block");
        }

        boolean allAdjacent = resolveBoolean(arguments, "shape", false,
                value -> "ALL_ADJACENT".equalsIgnoreCase(String.valueOf(value)));
        int maxBlocks = resolveMaxBlocks(arguments, allAdjacent);
        int maxDepth = resolveMaxDepth(arguments, allAdjacent);
        boolean respectFortune = resolveBoolean(arguments, "respect_fortune", defaultRespectFortune);
        boolean respectSilkTouch = resolveBoolean(arguments, "respect_silk_touch", defaultRespectSilkTouch);

        short veinBlockType = originBlockId.get();
        Set<WorldPosition> visited = new HashSet<>();
        Deque<VeinNode> queue = new ArrayDeque<>();
        List<WorldPosition> remainingPositions = new ArrayList<>();
        int affectedBlocks = 0;

        visited.add(origin);
        queue.add(new VeinNode(origin, 0));

        while (!queue.isEmpty()) {
            VeinNode node = queue.poll();
            WorldPosition position = node.position();

            if (node.depth() > maxDepth) {
                continue;
            }

            Optional<Short> numericId = blockQuery.findCustomBlockNumericId(position);
            if (numericId.isEmpty() || numericId.get() != veinBlockType) {
                continue;
            }

            if (!budgetView.tryConsume(position)) {
                remainingPositions.add(position);
                while (!queue.isEmpty()) {
                    remainingPositions.add(queue.poll().position());
                }
                if (affectedBlocks == 0) {
                    return new MechanicResult.Rejected("Budget exhausted");
                }
                return new MechanicResult.Partial(affectedBlocks, remainingPositions);
            }

            blockMutation.breakBlock(position);
            dropSink.dropFor(position, veinBlockType, dropCount(enchantmentView, respectFortune, respectSilkTouch));
            affectedBlocks++;

            if (affectedBlocks >= maxBlocks) {
                while (!queue.isEmpty()) {
                    remainingPositions.add(queue.poll().position());
                }
                return new MechanicResult.Partial(affectedBlocks, remainingPositions);
            }

            for (WorldPosition neighbor : adjacent(position, allAdjacent)) {
                if (visited.add(neighbor)) {
                    queue.add(new VeinNode(neighbor, node.depth() + 1));
                }
            }
        }

        return new MechanicResult.Done(affectedBlocks);
    }

    private int resolveMaxBlocks(Optional<MechanicArguments> arguments, boolean allAdjacent) {
        int resolved = resolveInt(arguments, "max_blocks", defaultMaxBlocks, 1, ABSOLUTE_MAX_BLOCKS);
        return allAdjacent ? Math.min(resolved, ALL_ADJACENT_MAX_BLOCKS) : resolved;
    }

    private int resolveMaxDepth(Optional<MechanicArguments> arguments, boolean allAdjacent) {
        int resolved = resolveInt(arguments, "max_depth", defaultMaxDepth, 1, HARD_MAX_DEPTH);
        return allAdjacent ? Math.min(resolved, ALL_ADJACENT_MAX_DEPTH) : resolved;
    }

    private int resolveInt(
            Optional<MechanicArguments> arguments,
            String key,
            int fallback,
            int min,
            int max) {
        return arguments
                .flatMap(args -> args.get(key))
                .map(value -> {
                    int parsed = ((Number) value).intValue();
                    if (parsed < min) {
                        return min;
                    }
                    if (parsed > max) {
                        return max;
                    }
                    return parsed;
                })
                .orElse(fallback);
    }

    private boolean resolveBoolean(Optional<MechanicArguments> arguments, String key, boolean fallback) {
        return resolveBoolean(arguments, key, fallback, value -> Boolean.parseBoolean(String.valueOf(value)));
    }

    private boolean resolveBoolean(
            Optional<MechanicArguments> arguments,
            String key,
            boolean fallback,
            java.util.function.Predicate<Object> predicate) {
        return arguments
                .flatMap(args -> args.get(key))
                .map(predicate::test)
                .orElse(fallback);
    }

    private int dropCount(
            Optional<EnchantmentView> enchantmentView,
            boolean respectFortune,
            boolean respectSilkTouch) {
        if (enchantmentView.isEmpty()) {
            return 1;
        }
        EnchantmentView view = enchantmentView.get();
        if (respectSilkTouch && view.getLevel("silk_touch").orElse(0) > 0) {
            return 1;
        }
        if (respectFortune) {
            int fortune = view.getLevel("fortune").orElse(0);
            if (fortune > 0) {
                return 1 + fortune;
            }
        }
        return 1;
    }

    private static List<WorldPosition> adjacent(WorldPosition pos, boolean allAdjacent) {
        String world = pos.worldName();
        int x = pos.x();
        int y = pos.y();
        int z = pos.z();
        if (!allAdjacent) {
            List<WorldPosition> face = new ArrayList<>(6);
            face.add(new WorldPosition(world, x + 1, y, z));
            face.add(new WorldPosition(world, x - 1, y, z));
            face.add(new WorldPosition(world, x, y + 1, z));
            face.add(new WorldPosition(world, x, y - 1, z));
            face.add(new WorldPosition(world, x, y, z + 1));
            face.add(new WorldPosition(world, x, y, z - 1));
            return face;
        }
        List<WorldPosition> all = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    all.add(new WorldPosition(world, x + dx, y + dy, z + dz));
                }
            }
        }
        return all;
    }

    private record VeinNode(WorldPosition position, int depth) {
    }
}
