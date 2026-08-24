package com.sharded.core.modules.trash;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** /trash - opens a disposal inventory; contents are deleted on close. */
public final class TrashModule extends Module implements CommandExecutor {

    private static final class TrashHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public TrashModule(ShardedCore plugin) {
        super(plugin, "trash");
    }

    @Override
    protected void onEnable() {
        registerCommand("trash", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.trash.use")) {
            send(player, "no-permission");
            return true;
        }
        int rows = Math.max(1, Math.min(6, config.getInt("rows", 4)));
        TrashHolder holder = new TrashHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(config.getString("title", "&8Trash &7(closes = deletes)")));
        holder.inventory = inventory;
        TrackedInventories.track(inventory, holder);
        player.openInventory(inventory);
        send(player, "opened");
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (TrackedInventories.untrack(event.getInventory(), TrashHolder.class) == null) return;
        boolean hadItems = !event.getInventory().isEmpty();
        event.getInventory().clear();
        if (hadItems && event.getPlayer() instanceof Player player) {
            if (config.getBoolean("play-sound", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
            }
            send(player, "emptied");
        }
    }
}
