package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.item.ItemService;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ItemCommandAdapter implements CommandExecutor {
    private final ItemService itemService;

    public ItemCommandAdapter(ItemService itemService) {
        this.itemService = Objects.requireNonNull(itemService, "itemService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this debug command.");
            return true;
        }
        if (args.length != 1) {
            return false;
        }
        if (!args[0].matches("[a-z][a-z0-9_]*")) {
            sender.sendMessage("Invalid custom item id: " + args[0]);
            return true;
        }
        CustomItemId id = new CustomItemId(args[0]);
        if (itemService.findItem(id).isEmpty()) {
            sender.sendMessage("Unknown custom item: " + id);
            return true;
        }
        sender.sendMessage("Custom item command foundation is registered for: " + id);
        return true;
    }
}
