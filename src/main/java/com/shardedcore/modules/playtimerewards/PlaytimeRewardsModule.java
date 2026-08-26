package com.shardedcore.modules.playtimerewards;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Items;
import com.shardedcore.util.Slots;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlaytimeRewardsModule extends Module implements CommandExecutor, Listener {

    private Sqlite sqlite;
    private final Map<UUID, Set<String>> claimed = new ConcurrentHashMap<>();

    public PlaytimeRewardsModule(ShardedCore plugin) {
        super(plugin, "playtimerewards");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS reward_claims (
                        uuid TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        reward TEXT NOT NULL,
                        PRIMARY KEY (uuid, kind, reward)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create reward_claims", ex);
        }
        registerCommand("playtimerewards", this);
        registerCommand("playtime", this);
        registerListener(this);
        if (config.getBoolean("notify.enabled", true)) {
            int interval = Math.max(10, config.getInt("notify.interval-seconds", 60));
            Bukkit.getScheduler().runTaskTimer(plugin, this::notifyReady, interval * 20L, interval * 20L);
        }
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> notify(event.getPlayer()), 40L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (command.getName().equalsIgnoreCase("playtime")) {
            send(player, "playtime", "playtime", format(playMillis(player)));
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        long played = playMillis(player);
        Set<String> owned = claimed(player.getUniqueId());
        List<String> ids = rewardIds();
        List<Integer> slots = Slots.parse(cfg("menu.reward-slots", "10-16,19-25,28-34,37-43"));
        int ready = readyCount(played, owned, ids);
        String next = nextLeft(played, owned, ids);
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Playtime | Rewards"), config.getInt("menu.rows", 6));
        for (int i = 0; i < ids.size() && i < slots.size(); i++) {
            String id = ids.get(i);
            ConfigurationSection reward = config.getConfigurationSection("rewards." + id);
            if (reward == null) continue;
            long required = Amounts.durationMillis(reward.getString("playtime", "30m"));
            String state = owned.contains(id) ? "claimed" : (played >= required ? "ready" : "locked");
            ItemStack icon = icon(player, reward, state, required, Math.max(0L, required - played), played);
            menu.set(slots.get(i), icon, event -> {
                event.setCancelled(true);
                if ("ready".equals(state)) claim(player, id, required);
                else open(player);
            });
        }
        if (config.getBoolean("menu.stats.enabled", true)) {
            ConfigurationSection stats = config.getConfigurationSection("menu.stats");
            menu.set(stats.getInt("slot", 49), Items.fromSection(stats, player,
                    "player", player.getName(),
                    "playtime", format(played),
                    "claimed", String.valueOf(owned.size()),
                    "total", String.valueOf(ids.size()),
                    "ready", String.valueOf(ready),
                    "next", next
            ), event -> {
                event.setCancelled(true);
                open(player);
            });
        }
        if (config.getBoolean("menu.filler.enabled", true)) {
            menu.fill(Items.fromSection(config.getConfigurationSection("menu.filler"), player));
        }
        plugin.menus().open(player, menu);
        Sounds.play(player, config.getConfigurationSection("sounds.open"));
    }

    private void claim(Player player, String id, long required) {
        Set<String> owned = claimed(player.getUniqueId());
        if (!owned.add(id)) {
            send(player, "already-claimed");
            return;
        }
        saveClaim(player.getUniqueId(), id);
        ConfigurationSection reward = config.getConfigurationSection("rewards." + id);
        if (reward != null) {
            for (String line : reward.getStringList("commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.replace("%player%", player.getName()));
            }
        }
        send(player, "claimed", "required", format(required));
        Sounds.play(player, config.getConfigurationSection("sounds.claim"));
        open(player);
    }

    private ItemStack icon(Player player, ConfigurationSection reward, String state, long required, long left, long played) {
        ConfigurationSection icon = config.getConfigurationSection("icons." + state);
        List<String> display = reward.getStringList("display");
        if (display.isEmpty()) display = List.of(cfg("icons.no-rewards", "&f- &7none"));
        List<String> lore = new ArrayList<>();
        for (String line : icon.getStringList("lore")) {
            if (line.contains("%rewards%")) lore.addAll(display);
            else lore.add(Text.apply(line, "required", format(required), "left", format(left), "playtime", format(played)));
        }
        String name = Text.apply(icon.getString("name", ""), "required", format(required), "left", format(left), "playtime", format(played));
        return new Items.ItemBuilder(Sounds.material(icon.getString("material", "STONE"), org.bukkit.Material.STONE))
                .name(name)
                .lore(lore)
                .glow(icon.getBoolean("glow", false))
                .hideAll()
                .build();
    }

    private void notifyReady() {
        for (Player player : Bukkit.getOnlinePlayers()) notify(player);
    }

    private void notify(Player player) {
        int ready = readyCount(playMillis(player), claimed(player.getUniqueId()), rewardIds());
        if (ready <= 0) return;
        send(player, "ready", "amount", String.valueOf(ready));
        Sounds.play(player, config.getConfigurationSection("sounds.notify"));
    }

    private int readyCount(long played, Set<String> owned, List<String> ids) {
        int ready = 0;
        for (String id : ids) {
            if (owned.contains(id)) continue;
            if (played >= Amounts.durationMillis(config.getString("rewards." + id + ".playtime", "999d"))) ready++;
        }
        return ready;
    }

    private String nextLeft(long played, Set<String> owned, List<String> ids) {
        long best = Long.MAX_VALUE;
        for (String id : ids) {
            if (owned.contains(id)) continue;
            long required = Amounts.durationMillis(config.getString("rewards." + id + ".playtime", "0"));
            if (required > played) best = Math.min(best, required - played);
        }
        return best == Long.MAX_VALUE ? cfg("time-format.none", "-") : format(best);
    }

    private String format(long millis) {
        return Amounts.duration(millis,
                cfg("time-format.days", "d"),
                cfg("time-format.hours", "h"),
                cfg("time-format.minutes", "m"),
                cfg("time-format.seconds", "s"),
                config.getInt("time-format.units", 2));
    }

    private long playMillis(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L;
    }

    private List<String> rewardIds() {
        ConfigurationSection section = config.getConfigurationSection("rewards");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    private Set<String> claimed(UUID uuid) {
        return claimed.computeIfAbsent(uuid, id -> {
            Set<String> set = ConcurrentHashMap.newKeySet();
            try {
                sqlite.query("SELECT reward FROM reward_claims WHERE uuid = ? AND kind = ?", rs -> {
                    try {
                        while (rs.next()) set.add(rs.getString("reward"));
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                    return set;
                }, id.toString(), "playtime");
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load playtime rewards", ex);
            }
            return set;
        });
    }

    private void saveClaim(UUID uuid, String reward) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("INSERT OR IGNORE INTO reward_claims (uuid, kind, reward) VALUES (?, ?, ?)",
                        uuid.toString(), "playtime", reward);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save playtime reward claim", ex);
            }
        });
    }
}
