package com.sharded.core.modules.tempranks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Temporary rank shop — /temprank shop */
public final class TempranksModule extends Module implements CommandExecutor, TabCompleter {

    public TempranksModule(ShardedCore plugin) {
        super(plugin, "tempranks");
    }

    @Override
    protected void onEnable() {
        File menuFile = new File(moduleFolder(), "tempranks.yml");
        ConfigSync.sync(plugin, menuFile, "modules/tempranks/tempranks.yml");
        plugin.gui().loadMenu(menuFile, "tempranks");

        registerCommand("temprank", this);
        registerCommand("rankshop", this);
        registerCommand("temprankshop", this);
    }

    @Override
    protected void onDisable() {
    }

    /** Called from GuiManager [temprank_buy] rank days tokens */
    public boolean tryPurchase(Player player, String rank, int days, long cost) {
        if (!player.hasPermission("sharded.tempranks.use")) {
            send(player, "no-permission");
            return false;
        }
        if (!plugin.luckPerms().isAvailable()) {
            send(player, "lp-missing");
            return false;
        }

        List<String> order = config.getStringList("rank-order");
        String rankId = rank.toLowerCase(Locale.ROOT);
        int targetIndex = plugin.luckPerms().rankIndex(order, rankId);

        if (plugin.luckPerms().hasPermanentGroup(player.getUniqueId(), rankId)) {
            send(player, "already-has-permanent", "%rank%", prettyRank(rankId));
            return false;
        }

        Optional<String> highestPermanent = plugin.luckPerms().highestPermanentRank(player.getUniqueId(), order);
        if (highestPermanent.isPresent()) {
            int ownedIndex = plugin.luckPerms().rankIndex(order, highestPermanent.get());
            if (ownedIndex >= targetIndex && targetIndex >= 0) {
                send(player, "has-higher-rank", "%rank%", prettyRank(highestPermanent.get()));
                return false;
            }
        }

        if (plugin.luckPerms().hasActiveTempGroup(player.getUniqueId(), rankId)) {
            Optional<Duration> left = plugin.luckPerms().tempGroupTimeLeft(player.getUniqueId(), rankId);
            String time = left.map(d -> Text.time(Math.max(1L, d.getSeconds()))).orElse("?");
            send(player, "already-has-temp", "%rank%", prettyRank(rankId), "%time%", time);
            return false;
        }

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

        String duration = days + "d";
        plugin.luckPerms().runConsole("lp user " + player.getName() + " parent addtemp " + rankId + " " + duration);
        send(player, "purchased", "%rank%", prettyRank(rankId), "%days%", String.valueOf(days));
        return true;
    }

    private String prettyRank(String rankId) {
        if (rankId == null || rankId.isBlank()) return rankId;
        return Character.toUpperCase(rankId.charAt(0)) + rankId.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.tempranks.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            plugin.gui().open(player, config.getString("main-menu", "tempranks"));
            return true;
        }
        send(player, "usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "shop");
        }
        return List.of();
    }
}
