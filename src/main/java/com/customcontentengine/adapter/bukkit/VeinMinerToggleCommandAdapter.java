package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.mechanic.PlayerPreferenceService;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Bukkit command that toggles the calling player's {@code vein_miner} preference
 * via the application-layer {@link PlayerPreferenceService}.
 */
public final class VeinMinerToggleCommandAdapter implements CommandExecutor {
    private final PlayerPreferenceService preferences;

    public VeinMinerToggleCommandAdapter(PlayerPreferenceService preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can toggle vein_miner.");
            return true;
        }
        String actorKey = player.getUniqueId().toString();
        boolean enabled = preferences.toggle(actorKey);
        sender.sendMessage("vein_miner " + (enabled ? "enabled" : "disabled") + ".");
        return true;
    }
}
