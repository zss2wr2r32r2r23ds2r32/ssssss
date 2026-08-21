package com.sharded.core.modules.crates;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/** ExcellentCrates crate preview/open hub (/crates). */
public final class CratesModule extends Module implements CommandExecutor {

    private CratesGuiHandler gui;

    public CratesModule(ShardedCore plugin) {
        super(plugin, "crates");
    }

    ShardedCore plugin() {
        return plugin;
    }

    @Override
    protected void onEnable() {
        syncJarResource("gui.yml");
        gui = new CratesGuiHandler(this, plugin);
        registerCommand("crates", this);
        registerCommand("crate", this);
    }

    @Override
    protected void onDisable() {
        gui = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.crates.use")) {
            send(player, "no-permission");
            return true;
        }
        gui.open(player);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof CratesGuiHandler.Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        gui.handleClick(player, event.getSlot(), event.getClick());
    }
}
