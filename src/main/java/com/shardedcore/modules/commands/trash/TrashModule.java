package com.shardedcore.modules.commands.trash;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TrashModule extends Module implements CommandExecutor, Listener {

    private static final class TrashHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public TrashModule(ShardedCore plugin) {
        super(plugin, "trash");
    }

    @Override
    public void enable() {
        registerListener(this);
        registerCommand("trash", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.trash")) {
            send(player, "no-permission");
            return true;
        }
        int rows = Math.max(1, Math.min(6, config.getInt("rows", 4)));
        TrashHolder holder = new TrashHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                ColorUtil.parse(config.getString("title", "&8Trash &7(closes = deletes)")));
        holder.inventory = inventory;
        TrackedInventories.track(inventory, holder);
        player.openInventory(inventory);
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (TrackedInventories.untrack(event.getInventory(), TrashHolder.class) == null) return;
        boolean hadItems = !event.getInventory().isEmpty();
        event.getInventory().clear();
        if (hadItems && event.getPlayer() instanceof Player player && config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
        }
    }
}
