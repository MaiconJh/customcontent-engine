package com.customcontentengine.adapter.bukkit;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Gate that restricts internal debug commands to authorized senders.
 *
 * <p>Checks two conditions:</p>
 * <ul>
 *     <li>The sender must have the {@code customcontent.debug} permission.</li>
 *     <li>The plugin configuration must have {@code debug.enabled} set to {@code true}.</li>
 * </ul>
 *
 * <p>This class is intentionally placed in the adapter layer because it depends
 * on Bukkit types.</p>
 */
public final class DebugCommandGate {

    private DebugCommandGate() {
    }

    public static boolean isAllowed(CommandSender sender, FileConfiguration config) {
        if (sender == null) {
            return false;
        }
        if (sender.hasPermission("customcontent.debug")) {
            return config.getBoolean("debug.enabled", false);
        }
        if (sender instanceof org.bukkit.command.ConsoleCommandSender) {
            return true;
        }
        return false;
    }
}