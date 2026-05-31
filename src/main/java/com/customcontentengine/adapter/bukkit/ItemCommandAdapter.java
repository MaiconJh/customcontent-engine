package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.item.ItemService;
import java.util.Map;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemCommandAdapter implements CommandExecutor {
    private static final String USAGE = "Usage: /givecustomitem <id>";

    private final ItemService<ItemStack> itemService;

    public ItemCommandAdapter(ItemService<ItemStack> itemService) {
        this.itemService = Objects.requireNonNull(itemService, "itemService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this debug command.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(USAGE);
            return true;
        }

        ItemService.ItemCreationResult<ItemStack> result = itemService.createCustomItem(args[0]);
        if (result.status() != ItemService.ItemCreationStatus.SUCCESS) {
            sender.sendMessage(result.message());
            return true;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result.item().orElseThrow());
        if (leftovers.isEmpty()) {
            sender.sendMessage("Custom item given: " + args[0]);
        } else {
            sender.sendMessage("Inventory full; custom item could not be fully added: " + args[0]);
        }
        return true;
    }
}
