package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Dev tool that reports the custom-block identity and current material at a world position, so
 * integration tests can assert on PDC persistence and world mutation from outside the server JVM.
 *
 * <p>Emits a single line of the form
 * {@code [query] x=<x> y=<y> z=<z> numericId=<id|none> material=<MATERIAL>}.</p>
 */
public final class DebugQueryCommandAdapter implements CommandExecutor {
    private final BlockStorePort blockStore;
    private final FileConfiguration config;

    public DebugQueryCommandAdapter(BlockStorePort blockStore, FileConfiguration config) {
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DebugCommandGate.isAllowed(sender, config)) {
            sender.sendMessage("You do not have permission to use this debug command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /debugquery <x> <y> <z> [world]");
            return true;
        }
        WorldPosition position = DebugPlaceCommandAdapter.parsePosition(args, 0);
        if (position == null) {
            sender.sendMessage("Invalid coordinates for /debugquery.");
            return true;
        }

        String numericId = blockStore.findNumericId(position)
                .map(String::valueOf)
                .orElse("none");
        String material = Optional.ofNullable(Bukkit.getWorld(position.worldName()))
                .map(world -> world.getBlockAt(position.x(), position.y(), position.z()).getType())
                .map(Material::name)
                .orElse("UNKNOWN");

        sender.sendMessage("[query] x=" + position.x() + " y=" + position.y() + " z=" + position.z()
                + " numericId=" + numericId + " material=" + material);
        return true;
    }
}
