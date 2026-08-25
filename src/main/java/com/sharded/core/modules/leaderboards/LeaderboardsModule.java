package com.sharded.core.modules.leaderboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /stats, /leaderboard, /leaderboards — paginated server leaderboards. */
public final class LeaderboardsModule extends Module implements CommandExecutor, TabCompleter {

    private LeaderboardService service;
    private LeaderboardGuiHandler gui;

    public LeaderboardsModule(ShardedCore plugin) {
        super(plugin, "leaderboards");
    }

    public LeaderboardService service() {
        return service;
    }

    ShardedCore plugin() {
        return plugin;
    }

    public int teamRank(int teamId) {
        return service != null ? service.teamRank(teamId) : -1;
    }

    public String hologramLineTemplate(String type) {
        return config.getString("hologram." + type + "-line",
                "&a#%rank% &f%name% &7— &f%value% %label%");
    }

    @Override
    protected void onEnable() {
        service = new LeaderboardService(plugin, config);
        gui = new LeaderboardGuiHandler(this, service, config);
        registerCommand("stats", this);
        registerCommand("leaderboard", this);
        registerCommand("leaderboards", this);
    }

    @Override
    protected void onDisable() {
        service = null;
        gui = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("stats")) {
            if (!player.hasPermission("sharded.stats.use")) {
                send(player, "no-permission");
                return true;
            }
            UUID target = player.getUniqueId();
            if (args.length >= 1) {
                if (!player.hasPermission("sharded.stats.others")) {
                    send(player, "no-permission");
                    return true;
                }
                OfflinePlayer other = OfflinePlayers.resolve(args[0]);
                target = other.getUniqueId();
            }
            gui.openStats(player, target);
            return true;
        }
        if (!player.hasPermission("sharded.leaderboards.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length >= 1) {
            String type = args[0].toLowerCase(Locale.ROOT);
            if (service.entries(type).isEmpty() && !isKnownType(type)) {
                send(player, "unknown-board", "%type%", type);
                return true;
            }
            gui.openBoard(player, type, 0);
            return true;
        }
        gui.openHub(player);
        return true;
    }

    private boolean isKnownType(String type) {
        return List.of("tokens", "token", "kills", "kill", "deaths", "death",
                "killstreaks", "killstreak", "streak", "playtime", "time", "teams", "team",
                "duels", "duels_wins", "wins").contains(type);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LeaderboardGuiHandler.Holder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), LeaderboardGuiHandler.Holder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        gui.handleClick(player, holder, event.getSlot());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("stats") && args.length == 1 && sender.hasPermission("sharded.stats.others")) {
            return TabCompleteHelper.knownPlayers(args[0]);
        }
        if ((cmd.equals("leaderboard") || cmd.equals("leaderboards")) && args.length == 1) {
            return TabCompleteHelper.filter(args[0], "tokens", "kills", "deaths", "killstreaks", "playtime", "teams", "duels", "wins");
        }
        return List.of();
    }
}
