package com.shardedcore.modules.coinflip;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.economy.EconomyService;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class CoinflipModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, Game> games = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, FlipSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, HistoryView> views = new ConcurrentHashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public CoinflipModule(ShardedCore plugin) {
        super(plugin, "coinflip");
    }

    @Override
    public void enable() {
        try {
            plugin.toggles().sqlite().run("""
                    CREATE TABLE IF NOT EXISTS coinflip_games (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            plugin.toggles().sqlite().run("""
                    CREATE TABLE IF NOT EXISTS coinflip_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        winner TEXT NOT NULL,
                        winner_name TEXT NOT NULL,
                        loser TEXT NOT NULL,
                        loser_name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        at INTEGER NOT NULL
                    )
                    """);
            plugin.toggles().sqlite().query(
                    "SELECT uuid, name, amount, created_at FROM coinflip_games",
                    rs -> {
                        try {
                            while (rs.next()) {
                                UUID uuid = UUID.fromString(rs.getString("uuid"));
                                games.put(uuid, new Game(uuid, rs.getString("name"),
                                        rs.getLong("amount"), rs.getLong("created_at")));
                            }
                        } catch (SQLException ignored) {
                        }
                        return null;
                    }
            );
            pruneHistory();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "[coinflip] Failed to open database", ex);
        }
        registerCommand("cf", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        for (FlipSession session : new HashSet<>(sessions.values())) {
            if (session.task != null) session.task.cancel();
            if (!session.finished) {
                EconomyService eco = economy();
                if (eco != null) {
                    eco.add(session.creator, session.amount);
                    eco.add(session.challenger, session.amount);
                }
            }
        }
        sessions.clear();
        searching.clear();
        views.clear();
        cooldowns.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.players-only");
            return true;
        }
        if (args.length == 0) {
            openGames(player, 0);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) {
                    send(player, "messages.usage-create");
                    yield true;
                }
                yield create(player, args[1]);
            }
            case "delete" -> delete(player);
            case "toggle" -> toggle(player);
            case "history" -> {
                String filter = null;
                if (args.length >= 2) {
                    org.bukkit.OfflinePlayer target = Players.offline(args[1]);
                    filter = target != null && target.getName() != null ? target.getName() : args[1];
                }
                openHistory(player, 0, filter);
                yield true;
            }
            default -> {
                send(player, "messages.usage");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tabs.filter(List.of("create", "delete", "toggle", "history"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Tabs.filter(List.of("10k", "100k", "1m", "100", "1000"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            return Tabs.players(args[1]);
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!searching.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (typed.isEmpty() || typed.equalsIgnoreCase("cancel")) {
                HistoryView view = views.getOrDefault(player.getUniqueId(), new HistoryView(0, null));
                openHistory(player, view.page, view.filter);
                return;
            }
            openHistory(player, 0, typed);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        searching.remove(uuid);
        views.remove(uuid);
    }

    private boolean create(Player player, String raw) {
        if (cannotPlay(player) || cooling(player)) return true;
        long amount = Amounts.parseLong(raw);
        if (amount <= 0) return fail(player, "messages.invalid-amount");
        long min = config.getLong("minimum", 10);
        long max = config.getLong("maximum", 0);
        if (amount < min) return fail(player, "messages.minimum", "amount", Amounts.format(min));
        if (max > 0 && amount > max) return fail(player, "messages.maximum", "amount", Amounts.format(max));
        if (games.containsKey(player.getUniqueId())) return fail(player, "messages.already-open");
        EconomyService eco = economy();
        if (eco.get(player.getUniqueId()) < amount || !eco.take(player.getUniqueId(), amount)) {
            return fail(player, "messages.cannot-afford");
        }
        Game game = new Game(player.getUniqueId(), player.getName(), amount, System.currentTimeMillis());
        if (games.putIfAbsent(player.getUniqueId(), game) != null) {
            eco.add(player.getUniqueId(), amount);
            return fail(player, "messages.already-open");
        }
        saveGame(game);
        Sounds.play(player, config.getConfigurationSection("sounds.create"));
        send(player, "messages.created-self", "player", player.getName(), "amount", Amounts.format(amount));
        broadcast("messages.created", player.getUniqueId(), "player", player.getName(), "amount", Amounts.format(amount));
        return true;
    }

    private boolean delete(Player player) {
        if (cooling(player)) return true;
        EconomyService eco = economy();
        if (eco == null) return fail(player, "messages.no-economy");
        if (eco.frozen(player.getUniqueId())) return fail(player, "messages.frozen");
        Game game = games.remove(player.getUniqueId());
        if (game == null) return fail(player, "messages.no-game");
        dropGame(player.getUniqueId());
        eco.add(player.getUniqueId(), game.amount);
        Sounds.play(player, config.getConfigurationSection("sounds.delete"));
        send(player, "messages.deleted", "player", player.getName(), "amount", Amounts.format(game.amount));
        return true;
    }

    private boolean toggle(Player player) {
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        if (settings == null) return true;
        settings.flipCoinflip(player);
        Sounds.play(player, config.getConfigurationSection("sounds.toggle"));
        return true;
    }

    private void openGames(Player player, int page) {
        List<Game> list = games.values().stream()
                .sorted(Comparator.comparingLong(Game::createdAt).reversed())
                .toList();
        int rows = config.getInt("menu.rows", 5);
        List<Integer> slots = innerSlots(rows);
        slots.remove(Integer.valueOf(config.getInt("menu.empty.slot", 22)));
        int per = Math.max(1, slots.size());
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Coinflip Games"), rows);
        menu.border(pane("menu.border"), (slot, item) -> {
        });
        menu.set(config.getInt("menu.stats.slot", 4), statsItem(player));
        if (list.isEmpty()) {
            menu.set(config.getInt("menu.empty.slot", 22), sectionItem("menu.empty", player));
        } else {
            int start = current * per;
            for (int i = 0; i < slots.size() && start + i < list.size(); i++) {
                Game game = list.get(start + i);
                menu.set(slots.get(i), gameHead(game), event -> {
                    event.setCancelled(true);
                    join(player, game.uuid());
                });
            }
        }
        menu.set(config.getInt("menu.previous.slot", 39), sectionItem("menu.previous", player, "page", String.valueOf(Math.max(1, current))), event -> {
            event.setCancelled(true);
            if (current > 0) {
                Sounds.play(player, config.getConfigurationSection("sounds.page"));
                openGames(player, current - 1);
            }
        });
        menu.set(config.getInt("menu.refresh.slot", 40), sectionItem("menu.refresh", player), event -> {
            event.setCancelled(true);
            Sounds.play(player, config.getConfigurationSection("sounds.page"));
            openGames(player, current);
        });
        menu.set(config.getInt("menu.next.slot", 41), sectionItem("menu.next", player, "page", String.valueOf(current + 2)), event -> {
            event.setCancelled(true);
            if (current + 1 < pages) {
                Sounds.play(player, config.getConfigurationSection("sounds.page"));
                openGames(player, current + 1);
            }
        });
        Sounds.play(player, config.getConfigurationSection("sounds.menu-open"));
        plugin.menus().open(player, menu);
    }

    private void openHistory(Player player, int page, String filter) {
        List<Flip> list = history(filter);
        int rows = config.getInt("history.rows", 6);
        List<Integer> slots = innerSlots(rows);
        int per = Math.max(1, slots.size());
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        views.put(player.getUniqueId(), new HistoryView(current, filter));
        String title = Text.apply(cfg("history.title", "&8Coinflip History [%page%]"), "page", String.valueOf(current + 1));
        Menus.Menu menu = plugin.menus().create(player, title, rows);
        menu.border(pane("menu.border"), (slot, item) -> {
        });
        int start = current * per;
        for (int i = 0; i < slots.size() && start + i < list.size(); i++) {
            menu.set(slots.get(i), historyItem(list.get(start + i)));
        }
        menu.set(config.getInt("history.previous.slot", 48), sectionItem("history.previous", player, "page", String.valueOf(Math.max(1, current))), event -> {
            event.setCancelled(true);
            if (current > 0) {
                Sounds.play(player, config.getConfigurationSection("sounds.page"));
                openHistory(player, current - 1, filter);
            }
        });
        menu.set(config.getInt("history.search.slot", 49), sectionItem("history.search", player), event -> {
            event.setCancelled(true);
            searching.add(player.getUniqueId());
            player.closeInventory();
            send(player, "messages.search-prompt");
        });
        menu.set(config.getInt("history.next.slot", 50), sectionItem("history.next", player, "page", String.valueOf(current + 2)), event -> {
            event.setCancelled(true);
            if (current + 1 < pages) {
                Sounds.play(player, config.getConfigurationSection("sounds.page"));
                openHistory(player, current + 1, filter);
            }
        });
        Sounds.play(player, config.getConfigurationSection("sounds.menu-open"));
        plugin.menus().open(player, menu);
    }

    private void join(Player player, UUID creatorId) {
        if (cannotPlay(player)) return;
        if (player.getUniqueId().equals(creatorId)) {
            fail(player, "messages.self");
            return;
        }
        Game game = games.remove(creatorId);
        if (game == null) {
            fail(player, "messages.gone");
            openGames(player, 0);
            return;
        }
        EconomyService eco = economy();
        if (eco.get(player.getUniqueId()) < game.amount || !eco.take(player.getUniqueId(), game.amount)) {
            games.put(creatorId, game);
            fail(player, "messages.cannot-afford");
            return;
        }
        dropGame(creatorId);
        startFlip(player, game);
    }

    private void startFlip(Player challenger, Game game) {
        int rows = config.getInt("animation.rows", 3);
        String title = cfg("animation.title", "&8Flipping Coin...");
        Menus.Menu challengerMenu = plugin.menus().create(challenger, title, rows);
        Player creatorOnline = Bukkit.getPlayer(game.uuid);
        Menus.Menu creatorMenu = creatorOnline != null ? plugin.menus().create(creatorOnline, title, rows) : null;
        FlipSession session = new FlipSession(game.uuid, challenger.getUniqueId(), game.name, challenger.getName(),
                game.amount, challengerMenu, creatorMenu);
        sessions.put(session.creator, session);
        sessions.put(session.challenger, session);
        paint(session);
        plugin.menus().open(challenger, challengerMenu);
        if (creatorOnline != null && creatorMenu != null) plugin.menus().open(creatorOnline, creatorMenu);
        long interval = Math.max(1L, config.getLong("animation.interval-ticks", 10));
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session), interval, interval);
    }

    private void tick(FlipSession session) {
        if (session.finished) {
            if (session.task != null) session.task.cancel();
            return;
        }
        session.step++;
        int steps = Math.max(1, config.getInt("animation.steps", 12));
        if (session.step < steps) {
            paint(session);
            return;
        }
        if (session.task != null) session.task.cancel();
        finishFlip(session);
    }

    private void paint(FlipSession session) {
        List<String> frames = config.getStringList("animation.frames");
        if (frames.isEmpty()) frames = List.of("YELLOW_STAINED_GLASS_PANE");
        boolean creatorTurn = session.step % 2 == 0;
        UUID uuid = creatorTurn ? session.creator : session.challenger;
        String name = creatorTurn ? session.creatorName : session.challengerName;
        paintMenu(session.challengerMenu, frames, session.step, uuid, name, session.amount);
        if (session.creatorMenu != null) {
            paintMenu(session.creatorMenu, frames, session.step, uuid, name, session.amount);
        }
        Player challenger = Bukkit.getPlayer(session.challenger);
        if (challenger != null) Sounds.play(challenger, config.getConfigurationSection("sounds.flip"));
        Player creator = Bukkit.getPlayer(session.creator);
        if (creator != null) Sounds.play(creator, config.getConfigurationSection("sounds.flip"));
    }

    private void paintMenu(Menus.Menu menu, List<String> frames, int step, UUID uuid, String name, long amount) {
        Material pane = Sounds.material(frames.get(Math.floorMod(step, frames.size())), Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack fill = Items.named(pane, " ", List.of());
        for (int i = 0; i < menu.inventory().getSize(); i++) menu.inventory().setItem(i, fill);
        String money = Amounts.format(amount);
        String title = Text.apply(cfg("animation.head.name", "&#FFE925&l%player%"), "player", name, "amount", money);
        List<String> lore = Text.applyList(config.getStringList("animation.head.lore"), "player", name, "amount", money);
        menu.set(config.getInt("animation.slot", 13), skull(uuid, title, lore));
    }

    private void finishFlip(FlipSession session) {
        session.finished = true;
        boolean creatorWins = ThreadLocalRandom.current().nextBoolean();
        UUID winner = creatorWins ? session.creator : session.challenger;
        UUID loser = creatorWins ? session.challenger : session.creator;
        String winnerName = creatorWins ? session.creatorName : session.challengerName;
        String loserName = creatorWins ? session.challengerName : session.creatorName;
        EconomyService eco = economy();
        if (eco != null) {
            double payout = session.amount * 2.0D;
            if (!eco.add(winner, payout)) eco.set(winner, eco.get(winner) + payout);
        }
        recordHistory(winner, winnerName, loser, loserName, session.amount);
        String money = Amounts.format(session.amount);
        Player winPlayer = Bukkit.getPlayer(winner);
        Player losePlayer = Bukkit.getPlayer(loser);
        if (winPlayer != null) {
            tellLines(winPlayer, "win", "player", loserName, "amount", money);
            Sounds.play(winPlayer, config.getConfigurationSection("sounds.win"));
        }
        if (losePlayer != null) {
            tellLines(losePlayer, "lose", "player", winnerName, "amount", money);
            Sounds.play(losePlayer, config.getConfigurationSection("sounds.lose"));
        }
        broadcast("messages.won-broadcast", null, "winner", winnerName, "loser", loserName, "amount", money);
        reveal(session, winner, winnerName);
        Bukkit.getScheduler().runTaskLater(plugin, () -> endSession(session),
                Math.max(1L, config.getLong("animation.hold-ticks", 20)));
    }

    private void reveal(FlipSession session, UUID winner, String winnerName) {
        ItemStack fill = Items.fromSection(config.getConfigurationSection("animation.reveal"),
                Bukkit.getPlayer(session.challenger));
        String money = Amounts.format(session.amount);
        String title = Text.apply(cfg("animation.head.name", "&#FFE925&l%player%"), "player", winnerName, "amount", money);
        List<String> lore = Text.applyList(config.getStringList("animation.head.lore"), "player", winnerName, "amount", money);
        ItemStack head = skull(winner, title, lore);
        int slot = config.getInt("animation.slot", 13);
        fillReveal(session.challengerMenu, fill, head, slot);
        if (session.creatorMenu != null) fillReveal(session.creatorMenu, fill, head, slot);
    }

    private void fillReveal(Menus.Menu menu, ItemStack fill, ItemStack head, int slot) {
        for (int i = 0; i < menu.inventory().getSize(); i++) menu.inventory().setItem(i, fill);
        menu.set(slot, head);
    }

    private void endSession(FlipSession session) {
        sessions.remove(session.creator, session);
        sessions.remove(session.challenger, session);
        closeIfViewing(session.challenger, session.challengerMenu);
        closeIfViewing(session.creator, session.creatorMenu);
    }

    private void closeIfViewing(UUID uuid, Menus.Menu menu) {
        if (menu == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.getOpenInventory().getTopInventory().equals(menu.inventory())) {
            player.closeInventory();
        }
    }

    private ItemStack statsItem(Player player) {
        Stats stats = stats(player.getUniqueId());
        int total = stats.wins + stats.losses;
        String rate = String.valueOf(total == 0 ? 0 : Math.round(stats.wins * 100.0D / total));
        String[] pairs = {
                "wins", String.valueOf(stats.wins),
                "losses", String.valueOf(stats.losses),
                "rate", rate,
                "profit", Amounts.format(stats.profit)
        };
        ConfigurationSection section = config.getConfigurationSection("menu.stats");
        String name = Text.apply(section == null ? "&#FFE925&lCOINFLIP" : section.getString("name", "&#FFE925&lCOINFLIP"), pairs);
        List<String> lore = Text.applyList(section == null ? List.of() : section.getStringList("lore"), pairs);
        return Items.named(statsMaterial(), name, lore);
    }

    private Material statsMaterial() {
        try {
            Material material = Sounds.material(cfg("menu.stats.material", "GUSTER_BANNER_PATTERN"), Material.PAPER);
            new ItemStack(material);
            return material;
        } catch (Throwable ignored) {
            return Material.PAPER;
        }
    }

    private ItemStack gameHead(Game game) {
        String money = Amounts.format(game.amount);
        String title = Text.apply(cfg("menu.head.name", "&#FFE925%player%'s Bet"), "player", game.name, "amount", money);
        List<String> lore = Text.applyList(config.getStringList("menu.head.lore"), "player", game.name, "amount", money);
        return skull(game.uuid, title, lore);
    }

    private ItemStack historyItem(Flip flip) {
        String money = Amounts.format(flip.amount);
        String time = when(flip.at);
        String title = Text.apply(cfg("history.entry.name", "&#80ee0b&l$%amount%"),
                "amount", money, "winner", flip.winnerName, "loser", flip.loserName, "time", time);
        List<String> lore = Text.applyList(config.getStringList("history.entry.lore"),
                "amount", money, "winner", flip.winnerName, "loser", flip.loserName, "time", time);
        return skull(flip.winner, title, lore);
    }

    private ItemStack skull(UUID uuid, String title, List<String> lore) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return Items.head(online, title, lore);
        return new Items.ItemBuilder(Material.PLAYER_HEAD).edit(meta -> {
            if (meta instanceof SkullMeta skull) skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(ColorUtil.parse(title));
            List<Component> lines = new ArrayList<>();
            if (lore != null) for (String line : lore) lines.add(ColorUtil.parse(line));
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.values());
        }).hideAll().build();
    }

    private ItemStack sectionItem(String path, Player player, String... pairs) {
        return Items.fromSection(config.getConfigurationSection(path), player, pairs);
    }

    private ItemStack pane(String path) {
        return Items.named(
                Sounds.material(cfg(path + ".material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                cfg(path + ".name", " "),
                List.of()
        );
    }

    private List<Integer> innerSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        int size = Math.max(1, Math.min(6, rows)) * 9;
        int last = rows - 1;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == last || col == 0 || col == 8) continue;
            slots.add(i);
        }
        return slots;
    }

    private boolean cannotPlay(Player player) {
        EconomyService eco = economy();
        if (eco == null) return fail(player, "messages.no-economy");
        if (eco.frozen(player.getUniqueId())) return fail(player, "messages.frozen");
        if (config.getBoolean("block-in-combat", true)) {
            CombatModule combat = plugin.modules().get(CombatModule.class);
            if (combat != null && combat.tagged(player)) return fail(player, "messages.in-combat");
        }
        if (sessions.containsKey(player.getUniqueId())) return fail(player, "messages.too-fast");
        return false;
    }

    private boolean cooling(Player player) {
        int seconds = config.getInt("cooldown-seconds", 3);
        if (seconds <= 0) return false;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < seconds * 1000L) return fail(player, "messages.too-fast");
        cooldowns.put(player.getUniqueId(), now);
        return false;
    }

    private boolean fail(Player player, String path, String... pairs) {
        send(player, path, pairs);
        Sounds.play(player, config.getConfigurationSection("sounds.error"));
        return true;
    }

    private void tellLines(Player player, String path, String... pairs) {
        for (String line : config.getStringList(path)) {
            player.sendMessage(ColorUtil.parse(Text.apply(line, pairs)));
        }
    }

    private void broadcast(String path, UUID skip, String... pairs) {
        String message = Text.apply(cfg(path, ""), pairs);
        if (message.isEmpty()) return;
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (skip != null && viewer.getUniqueId().equals(skip)) continue;
            if (settings != null && !settings.coinflip(viewer)) continue;
            viewer.sendMessage(ColorUtil.parse(message));
        }
    }

    private EconomyService economy() {
        EconomyModule module = plugin.modules().get(EconomyModule.class);
        return module == null ? null : module.service();
    }

    private void saveGame(Game game) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.toggles().sqlite().execute(
                        "INSERT OR REPLACE INTO coinflip_games (uuid, name, amount, created_at) VALUES (?, ?, ?, ?)",
                        game.uuid.toString(), game.name, game.amount, game.createdAt);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "[coinflip] Failed to save game", ex);
            }
        });
    }

    private void dropGame(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.toggles().sqlite().execute("DELETE FROM coinflip_games WHERE uuid = ?", uuid.toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "[coinflip] Failed to delete game", ex);
            }
        });
    }

    private void recordHistory(UUID winner, String winnerName, UUID loser, String loserName, long amount) {
        long at = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.toggles().sqlite().execute(
                        "INSERT INTO coinflip_history (winner, winner_name, loser, loser_name, amount, at) VALUES (?, ?, ?, ?, ?, ?)",
                        winner.toString(), winnerName, loser.toString(), loserName, amount, at);
                pruneHistory();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "[coinflip] Failed to record history", ex);
            }
        });
    }

    private void pruneHistory() {
        int days = config.getInt("history.keep-days", 30);
        if (days <= 0) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.toggles().sqlite().execute(
                        "DELETE FROM coinflip_history WHERE at < ?",
                        System.currentTimeMillis() - days * 86400000L);
            } catch (SQLException ignored) {
            }
        });
    }

    private List<Flip> history(String filter) {
        try {
            List<Flip> result = plugin.toggles().sqlite().query(
                    "SELECT winner, winner_name, loser, loser_name, amount, at FROM coinflip_history ORDER BY at DESC LIMIT 500",
                    rs -> {
                        List<Flip> out = new ArrayList<>();
                        try {
                            while (rs.next()) {
                                Flip flip = new Flip(
                                        UUID.fromString(rs.getString("winner")),
                                        rs.getString("winner_name"),
                                        UUID.fromString(rs.getString("loser")),
                                        rs.getString("loser_name"),
                                        rs.getLong("amount"),
                                        rs.getLong("at")
                                );
                                if (filter == null || filter.isBlank() || matches(flip, filter)) out.add(flip);
                            }
                        } catch (SQLException ignored) {
                        }
                        return out;
                    }
            );
            return result == null ? List.of() : result;
        } catch (SQLException ex) {
            return List.of();
        }
    }

    private boolean matches(Flip flip, String filter) {
        String token = filter.toLowerCase(Locale.ROOT);
        return flip.winnerName.toLowerCase(Locale.ROOT).contains(token)
                || flip.loserName.toLowerCase(Locale.ROOT).contains(token);
    }

    private Stats stats(UUID uuid) {
        String id = uuid.toString();
        try {
            Stats result = plugin.toggles().sqlite().query(
                    "SELECT winner, loser, amount FROM coinflip_history WHERE winner = ? OR loser = ?",
                    rs -> {
                        int wins = 0;
                        int losses = 0;
                        long profit = 0;
                        try {
                            while (rs.next()) {
                                long amount = rs.getLong("amount");
                                if (id.equals(rs.getString("winner"))) {
                                    wins++;
                                    profit += amount;
                                } else {
                                    losses++;
                                    profit -= amount;
                                }
                            }
                        } catch (SQLException ignored) {
                        }
                        return new Stats(wins, losses, profit);
                    },
                    id, id
            );
            return result == null ? new Stats(0, 0, 0) : result;
        } catch (SQLException ex) {
            return new Stats(0, 0, 0);
        }
    }

    private String when(long at) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(cfg("history.time-format", "dd.MM.yyyy HH:mm"))
                    .withZone(ZoneId.systemDefault());
            return formatter.format(Instant.ofEpochMilli(at));
        } catch (Exception ex) {
            return String.valueOf(at);
        }
    }

    private record Game(UUID uuid, String name, long amount, long createdAt) {
    }

    private record Flip(UUID winner, String winnerName, UUID loser, String loserName, long amount, long at) {
    }

    private record Stats(int wins, int losses, long profit) {
    }

    private record HistoryView(int page, String filter) {
    }

    private static final class FlipSession {
        private final UUID creator;
        private final UUID challenger;
        private final String creatorName;
        private final String challengerName;
        private final long amount;
        private final Menus.Menu challengerMenu;
        private final Menus.Menu creatorMenu;
        private BukkitTask task;
        private int step;
        private boolean finished;

        private FlipSession(UUID creator, UUID challenger, String creatorName, String challengerName, long amount,
                            Menus.Menu challengerMenu, Menus.Menu creatorMenu) {
            this.creator = creator;
            this.challenger = challenger;
            this.creatorName = creatorName;
            this.challengerName = challengerName;
            this.amount = amount;
            this.challengerMenu = challengerMenu;
            this.creatorMenu = creatorMenu;
        }
    }
}
