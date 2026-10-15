package com.sharded.core.modules.stats;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.NumberUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.modules.leaderboardboards.LeaderboardBoardsModule;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.setup.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class StatsModule extends SetupFeatureModule implements CommandExecutor, TabCompleter, Listener {

    private StatsData statsData;
    private BukkitTask playtimeTask;
    private final Map<UUID, Long> sessionStart = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);

    public StatsModule(ShardedCore plugin) {
        super(plugin, "stats");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        migrateLoreIfNeeded();
        statsData = new StatsData(plugin);
        statsData.load();
        if (plugin.getCommand("stats") != null) {
            plugin.getCommand("stats").setExecutor(this);
            plugin.getCommand("stats").setTabCompleter(this);
        }
        this.playtimeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushPlaytime, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickOpenStats, 100L, 100L);
    }

    private void migrateLoreIfNeeded() {
        FileConfiguration bundled = loadBundledConfig();
        int jar = bundled != null ? bundled.getInt("lore-version", 6) : 6;
        int live = config.getInt("lore-version", 0);
        if (live >= jar || bundled == null) {
            return;
        }
        if (bundled.contains("items.playtime")) {
            config.set("items.playtime", bundled.get("items.playtime"));
        }
        if (bundled.contains("items.tokens")) {
            config.set("items.tokens", bundled.get("items.tokens"));
        }
        if (bundled.contains("slots.tokens")) {
            config.set("slots.tokens", bundled.get("slots.tokens"));
        }
        config.set("slots.shards", null);
        config.set("items.shards", null);
        if (bundled.contains("slots.money")) {
            config.set("slots.money", bundled.get("slots.money"));
        }
        if (bundled.contains("slots.refresh")) {
            config.set("slots.refresh", bundled.get("slots.refresh"));
        }
        if (bundled.contains("items.refresh")) {
            config.set("items.refresh", bundled.get("items.refresh"));
        }
        if (bundled.contains("refresh-interval-seconds")) {
            config.set("refresh-interval-seconds", bundled.get("refresh-interval-seconds"));
        }
        config.set("lore-version", jar);
        saveConfig();
    }

    @Override
    protected void onDisable() {
        if (this.playtimeTask != null) {
            this.playtimeTask.cancel();
            this.playtimeTask = null;
        }
        flushPlaytime();
        if (statsData != null) {
            statsData.save();
        }
        // listeners unregistered by Module.disable()
    }

    private void flushPlaytime() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : sessionStart.entrySet()) {
            StatsData.PlayerStats stats = statsData.get(entry.getKey());
            stats.playtimeMillis += Math.max(0L, now - entry.getValue());
            entry.setValue(now);
        }
        if (statsData != null) {
            statsData.save();
        }
    }

    public StatsData.PlayerStats getStats(UUID uuid) {
        return statsData.get(uuid);
    }

    public StatsData getStatsData() {
        return statsData;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("players-only", "Players only."));
            return true;
        }
        OfflinePlayer target = player;
        if (args.length >= 1) {
            OfflinePlayer found = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (found == null || (!found.hasPlayedBefore() && !found.isOnline())) {
                UUID offline = statsData.findByName(args[0]);
                if (offline == null) {
                    TextUtil.sendActionBar(player, prefix() + plugin.errorColor()
                            + msg("player-not-found", "Player not found."));
                    return true;
                }
                found = Bukkit.getOfflinePlayer(offline);
            }
            target = found;
        }
        openStats(player, target);
        return true;
    }

    private void openStats(Player viewer, OfflinePlayer target) {
        String title = config.getString("gui-title", "&8%player%'s Stats")
                .replace("%player%", target.getName() == null ? "Player" : target.getName());
        Inventory inventory = GuiHelper.create("stats:" + target.getUniqueId(), title, config.getInt("rows", 5));
        paintStats(inventory, viewer, target);
        viewer.openInventory(inventory);
        SoundUtil.play(viewer, config.getString("sound-open", "block.note_block.pling"));
    }

    private void paintStats(Inventory inventory, Player viewer, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        StatsData.PlayerStats stats = statsData.get(uuid);
        syncLiveStats(target, stats);

        switch (config.getString("filler-style", "glass").toLowerCase()) {
            case "dark" -> GuiHelper.fillDarkGlass(inventory);
            case "bordered-inner", "bordered" -> GuiHelper.fillBorderedInner(inventory);
            case "border" -> GuiHelper.fillBorder(inventory);
            default -> GuiHelper.fillGlass(inventory);
        }

        String teamName = "N/A";
        TeamsModule teams = plugin.modules().get(TeamsModule.class);
        if (teams != null) {
            String resolved = teams.getTeamName(uuid);
            if (resolved != null && !resolved.isBlank()) {
                teamName = resolved;
            }
        }

        boolean viewingSelf = viewer.getUniqueId().equals(uuid);
        String youOrPlayer = viewingSelf ? "You" : stats.name;
        String haveOrHas = viewingSelf ? "Have" : "Has";
        String teamSubjectLine = viewingSelf ? "Your In" : stats.name + " is In";
        String headViewLine1 = viewingSelf ? "View Your own" : "View " + stats.name + "'s";
        String headViewLine2 = viewingSelf ? "Personal &#0098FF&nStats" : "Personal &#0098FF&nStats";

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", stats.name);
        placeholders.put("%you_or_player%", youOrPlayer);
        placeholders.put("%your_or_player%", viewingSelf ? "Your" : stats.name);
        placeholders.put("%have_or_has%", haveOrHas);
        placeholders.put("%team_subject_line%", teamSubjectLine);
        placeholders.put("%subject%", youOrPlayer);
        placeholders.put("%verb%", haveOrHas);
        placeholders.put("%head_view_line1%", headViewLine1);
        placeholders.put("%head_view_line2%", headViewLine2);
        placeholders.put("%first_join%", stats.firstJoin <= 0 ? "N/A" : dateFormat.format(new Date(stats.firstJoin)));
        placeholders.put("%joins%", String.valueOf(Math.max(stats.joins, 1)));
        placeholders.put("%kills%", NumberUtil.formatComma(stats.kills));
        placeholders.put("%deaths%", NumberUtil.formatComma(stats.deaths));
        placeholders.put("%playtime%", formatPlaytime(stats.playtimeMillis));
        placeholders.put("%totems%", NumberUtil.formatComma(stats.totems));
        placeholders.put("%team%", teamName);
        long tokens = tokenBalance(uuid);
        placeholders.put("%tokens%", NumberUtil.formatComma(tokens));
        placeholders.put("%money%", NumberUtil.formatShort(plugin.playerData().getMoney(uuid)));
        placeholders.put("%time%", formatCountdown(refreshRemainingMs()));

        inventory.setItem(config.getInt("slots.head", 13), buildHead(target, placeholders, viewingSelf));
        setConfiguredItem(inventory, "kills", Material.NETHERITE_SWORD, placeholders);
        setConfiguredItem(inventory, "deaths", Material.SKELETON_SKULL, placeholders);
        setConfiguredItem(inventory, "playtime", Material.CLOCK, placeholders);
        setConfiguredItem(inventory, "totems", Material.TOTEM_OF_UNDYING, placeholders);
        setConfiguredItem(inventory, "team", Material.WHITE_BANNER, placeholders);
        Material tokenMat = MaterialUtil.resolveWithFallbacks(
                config.getString("items.tokens.material"),
                config.getString("items.tokens.fallback"),
                "LIGHT_BLUE_BUNDLE", "BUNDLE");
        setConfiguredItem(inventory, "tokens", tokenMat != null ? tokenMat : Material.BUNDLE, placeholders);
        if (config.getInt("slots.money", -1) >= 0) {
            Material moneyMat = MaterialUtil.resolve(config.getString("items.money.material"), Material.BUNDLE);
            setConfiguredItem(inventory, "money", moneyMat, placeholders);
        }
        // Refresh clock removed from /stats.
    }

    private long tokenBalance(UUID uuid) {
        TokensModule tokensModule = plugin.modules().get(TokensModule.class);
        if (tokensModule != null && tokensModule.service() != null) {
            return tokensModule.service().getBalance(uuid);
        }
        return 0L;
    }

    public void openStatsFor(Player viewer, OfflinePlayer target) {
        openStats(viewer, target);
    }

    private void syncLiveStats(OfflinePlayer target, StatsData.PlayerStats stats) {
        Player online = target instanceof Player player ? player : Bukkit.getPlayer(target.getUniqueId());
        OfflinePlayer source = online != null ? online : target;
        if (source.getName() != null && !source.getName().isBlank()) {
            stats.name = source.getName();
        }
        long firstPlayed = source.getFirstPlayed();
        if (firstPlayed > 0L) {
            stats.firstJoin = firstPlayed;
        }
        if (online != null) {
            stats.kills = statistic(online, Statistic.PLAYER_KILLS);
            stats.deaths = statistic(online, Statistic.DEATHS);
            stats.playtimeMillis = (long) statistic(online, Statistic.PLAY_ONE_MINUTE) * 50L;
            int totems = itemStatistic(online, Material.TOTEM_OF_UNDYING);
            if (totems > stats.totems) {
                stats.totems = totems;
            }
            return;
        }
        int vanillaKills = statistic(source, Statistic.PLAYER_KILLS);
        int vanillaDeaths = statistic(source, Statistic.DEATHS);
        long vanillaPlayMs = (long) statistic(source, Statistic.PLAY_ONE_MINUTE) * 50L;
        if (vanillaKills > 0) {
            stats.kills = vanillaKills;
        }
        if (vanillaDeaths > 0) {
            stats.deaths = vanillaDeaths;
        }
        if (vanillaPlayMs > 0L) {
            stats.playtimeMillis = vanillaPlayMs;
        }
    }

    private static int statistic(OfflinePlayer player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int itemStatistic(Player player, Material material) {
        try {
            return player.getStatistic(Statistic.USE_ITEM, material);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void placeRefreshClock(Inventory inventory, String time) {
        int tokenSlot = config.getInt("slots.tokens", 31);
        int slot = config.getInt("slots.refresh", tokenSlot + 9);
        if (slot < 0 || slot >= inventory.getSize()) {
            slot = Math.min(inventory.getSize() - 1, tokenSlot + 9);
        }
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        List<String> lore = new ArrayList<>();
        List<String> template = config.getStringList("items.refresh.lore");
        if (template.isEmpty()) {
            template = List.of(
                    "&8Stats",
                    "",
                    "&#FCFF00Information:",
                    "&#FCFF00| &fStats refresh every 2 minutes",
                    "",
                    "&#FCFF00☀ &fTime: &#FCFF00%time%"
            );
        }
        for (String line : template) {
            lore.add(line.replace("%time%", time).replace("%timer%", time));
        }
        inventory.setItem(slot, ItemBuilder.of(Material.CLOCK)
                .name(config.getString("items.refresh.name", "&#FCFF00&lREFRESHES IN &f%time%")
                        .replace("%time%", time))
                .lore(lore)
                .hideExtras()
                .build());
    }

    private void tickOpenStats() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)) {
                continue;
            }
            String id = holder.getId();
            if (id == null || !id.startsWith("stats:")) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(id.substring("stats:".length()));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            OfflinePlayer target = Bukkit.getPlayer(uuid);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(uuid);
            }
            paintStats(player.getOpenInventory().getTopInventory(), player, target);
        }
    }

    private long refreshRemainingMs() {
        LeaderboardBoardsModule holograms = plugin.modules().get(LeaderboardBoardsModule.class);
        if (holograms != null) {
            return holograms.hologramRefreshRemainingMs();
        }
        long interval = Math.max(5L, config.getLong("refresh-interval-seconds", 120L)) * 1000L;
        long rem = interval - System.currentTimeMillis() % interval;
        return rem == interval ? 0L : rem;
    }

    private static String formatCountdown(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long minutes = total / 60L;
        long seconds = total % 60L;
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private void setConfiguredItem(Inventory inventory, String key, Material fallback, Map<String, String> placeholders) {
        int slot = config.getInt("slots." + key, -1);
        if (slot < 0) {
            return;
        }
        Material material = MaterialUtil.resolve(config.getString("items." + key + ".material"), fallback);
        inventory.setItem(slot, buildItem(material,
                config.getString("items." + key + ".name", key),
                config.getStringList("items." + key + ".lore"),
                placeholders));
    }

    private ItemStack buildHead(OfflinePlayer target, Map<String, String> placeholders, boolean viewingSelf) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(TextUtil.itemComponent(apply(config.getString("items.head.name", "%player%"), placeholders)));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            List<String> loreLines = config.getStringList(viewingSelf ? "items.head.lore" : "items.head.lore-other");
            if (loreLines.isEmpty()) {
                loreLines = config.getStringList("items.head.lore");
            }
            for (String line : loreLines) {
                lore.add(TextUtil.itemComponent(apply(line, placeholders)));
            }
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack buildItem(Material material, String name, List<String> loreLines, Map<String, String> placeholders) {
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(apply(line, placeholders));
        }
        return ItemBuilder.of(material).name(apply(name, placeholders)).lore(lore).hideExtras().build();
    }

    private String apply(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String formatPlaytime(long millis) {
        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        StatsData.PlayerStats stats = statsData.get(player.getUniqueId());
        stats.name = player.getName();
        if (stats.firstJoin <= 0) {
            stats.firstJoin = System.currentTimeMillis();
        }
        stats.joins++;
        sessionStart.put(player.getUniqueId(), System.currentTimeMillis());
        syncLiveStats(player, stats);
        statsData.save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Long start = sessionStart.remove(uuid);
        if (start != null) {
            statsData.get(uuid).playtimeMillis += Math.max(0L, System.currentTimeMillis() - start);
            statsData.save();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        statsData.get(victim.getUniqueId()).deaths++;
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            statsData.get(killer.getUniqueId()).kills++;
        }
        statsData.save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            statsData.get(player.getUniqueId()).totems++;
            statsData.save();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder
                && holder.getId() != null && holder.getId().startsWith("stats")) {
            event.setCancelled(true);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            for (String known : statsData.knownNames()) {
                if (known.toLowerCase(Locale.ROOT).startsWith(prefix) && !names.contains(known)) {
                    names.add(known);
                }
            }
            return names;
        }
        return List.of();
    }

    public String prefix() {
        return messages.getString("prefix", "&#9FFF00&lSTATS &8▷ &r");
    }
}
