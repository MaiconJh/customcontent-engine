package com.customcontentengine.integration;

import com.customcontentengine.bootstrap.CustomContentPlugin;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class TestProtectionCommandAdapter implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /testprotection <enable|disable> [minX]");
            return true;
        }
        CustomContentPlugin plugin = JavaPlugin.getPlugin(CustomContentPlugin.class);
        if (!(plugin instanceof TestCustomContentPlugin testPlugin)) {
            sender.sendMessage("testprotection failed: plugin is not TestCustomContentPlugin");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "enable" -> {
                int minX = args.length > 1 ? Integer.parseInt(args[1]) : 32;
                testPlugin.setProtectionPort(new TestProtectionPort(minX));
                sender.sendMessage("testprotection enabled with minX=" + minX);
            }
            case "disable" -> {
                testPlugin.setProtectionPort(null);
                sender.sendMessage("testprotection disabled");
            }
            default -> sender.sendMessage("Unknown subcommand: " + args[0]);
        }
        return true;
    }
}
