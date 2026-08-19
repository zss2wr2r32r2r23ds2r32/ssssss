package com.sharded.core.modules.abilities;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.Numbers;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/** Token shop for temporary ability permissions (7-day LuckPerms grants). */
public final class AbilitiesShopModule extends Module implements CommandExecutor {

    public AbilitiesShopModule(ShardedCore plugin) {
        super(plugin, "abilities");
    }

    @Override
    protected void onEnable() {
        File menuFile = new File(moduleFolder(), "shop.yml");
        ConfigSync.sync(plugin, menuFile, "modules/abilities/shop.yml");
        plugin.gui().loadMenu(menuFile, "abilities");
    }

    @Override
    protected void onDisable() {
    }

    /** Called from GuiManager [ability_buy] permission days tokens */
    public boolean tryPurchase(Player player, String permission, int days, long cost) {
        if (!player.hasPermission("sharded.tokenshop.use")) {
            send(player, "no-permission");
            return false;
        }
        if (!plugin.luckPerms().isAvailable()) {
            send(player, "lp-missing");
            return false;
        }
        if (permission == null || permission.isBlank()) return false;

        TokenService tokens = plugin.modules().tokens();
        if (tokens == null) return false;
        long balance = tokens.getBalance(player.getUniqueId());
        if (balance < cost) {
            send(player, "not-enough-tokens", "%missing%", Numbers.format(cost - balance));
            return false;
        }
        if (!tokens.take(player.getUniqueId(), cost)) {
            send(player, "not-enough-tokens", "%missing%", Numbers.format(cost - balance));
            return false;
        }

        String duration = Math.max(1, days) + "d";
        plugin.luckPerms().runConsole("lp user " + player.getName()
                + " permission settemp " + permission + " true " + duration);
        send(player, "purchased", "%ability%", prettyPermission(permission), "%days%", String.valueOf(days));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    private String prettyPermission(String permission) {
        String name = permission;
        if (name.startsWith("sharded.")) name = name.substring("sharded.".length());
        name = name.replace('.', ' ');
        if (name.isEmpty()) return permission;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.tokenshop.use")) {
            send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, "abilities");
        return true;
    }
}
