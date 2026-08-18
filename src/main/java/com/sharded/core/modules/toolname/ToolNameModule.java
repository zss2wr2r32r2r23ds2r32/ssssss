package com.sharded.core.modules.toolname;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

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
        if (containsBlacklisted(name)) {
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

    private boolean containsBlacklisted(String input) {
        String lower = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        List<String> blacklist = config.getStringList("blacklist");
        for (String word : blacklist) {
            if (word.isBlank()) continue;
            String w = word.toLowerCase(Locale.ROOT).trim();
            if (lower.contains(w)) return true;
        }
        return false;
    }
}
