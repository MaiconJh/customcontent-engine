package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.mechanic.AreaBreakRuntimeService;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AreaBreakDebugCommandAdapter implements CommandExecutor {
    private static final int MAX_TARGET_DISTANCE = 6;

    private final AreaBreakRuntimeService areaBreakRuntime;

    public AreaBreakDebugCommandAdapter(AreaBreakRuntimeService areaBreakRuntime) {
        this.areaBreakRuntime = Objects.requireNonNull(areaBreakRuntime, "areaBreakRuntime");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this internal debug command.");
            return true;
        }

        Block target = player.getTargetBlockExact(MAX_TARGET_DISTANCE);
        if (target == null) {
            sender.sendMessage("No target block in range for area_break debug.");
            return true;
        }

        WorldPosition origin = toWorldPosition(target.getLocation());
        MechanicResult result = areaBreakRuntime.execute(origin, player.getUniqueId().toString());
        if (result instanceof MechanicResult.Done done) {
            sender.sendMessage("area_break debug done: " + done.affectedBlocks() + " block(s).");
        } else if (result instanceof MechanicResult.Partial partial) {
            sender.sendMessage("area_break debug partial: " + partial.affectedBlocks()
                    + " block(s), " + partial.remaining().size() + " remaining.");
        } else if (result instanceof MechanicResult.Rejected rejected) {
            sender.sendMessage("area_break debug rejected: " + rejected.reason());
        }
        return true;
    }

    private WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
