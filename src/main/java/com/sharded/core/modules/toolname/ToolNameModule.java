package com.sharded.core.modules.toolname;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import com.sharded.core.util.WordBlacklist;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** /toolname [name] - rename the held item with a configurable word blacklist. */
public final class ToolNameModule extends Module implements CommandExecutor {

    public ToolNameModule(ShardedCore plugin) {
        super(plugin, "toolname");
    }

    @Override
    protected void onEnable() {
        registerCommand("toolname", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.toolname.use")) {
            send(player, "no-permission");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            send(player, "no-item");
            return true;
        }
        if (args.length == 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                meta.displayName(null);
                item.setItemMeta(meta);
            }
            send(player, "cleared");
            return true;
        }
        String name = String.join(" ", args);
        if (WordBlacklist.contains(config, "blacklist", name)) {
            send(player, "blacklisted");
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            send(player, "failed");
            return true;
        }
        meta.displayName(Text.c(name));
        item.setItemMeta(meta);
        send(player, "renamed", "%name%", name);
        return true;
    }
}
