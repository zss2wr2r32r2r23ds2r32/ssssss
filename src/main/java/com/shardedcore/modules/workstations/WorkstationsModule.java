package com.shardedcore.modules.workstations;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class WorkstationsModule extends Module implements CommandExecutor {

    public WorkstationsModule(ShardedCore plugin) {
        super(plugin, "workstations");
    }

    @Override
    public void enable() {
        registerCommand("trash", this);
        registerCommand("craft", this);
        registerCommand("anvil", this);
        registerCommand("toolsmith", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.players-only");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "trash" -> {
                Menus.Menu menu = plugin.menus().create(player, cfg("trash-title", "&8Trash"), config.getInt("trash-rows", 4)).unlocked();
                menu.onClose(closed -> {
                    for (ItemStack item : closed.getOpenInventory().getTopInventory().getContents()) {
                        if (item != null && !item.getType().isAir()) item.setAmount(0);
                    }
                });
                menu.fill(Items.named(Material.AIR, " ", List.of()));
                plugin.menus().open(player, menu);
            }
            case "craft", "wb", "workbench" -> player.openWorkbench(null, true);
            case "anvil" -> player.openInventory(Bukkit.createInventory(player, InventoryType.ANVIL));
            case "toolsmith", "smithing", "smithingtable" ->
                    player.openInventory(Bukkit.createInventory(player, InventoryType.SMITHING));
            default -> {
            }
        }
        return true;
    }
}
