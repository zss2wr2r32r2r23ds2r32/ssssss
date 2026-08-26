package com.shardedcore.modules.coinflip;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.economy.EconomyService;
import com.shardedcore.util.Numbers;
import com.shardedcore.util.OfflinePlayers;
import com.shardedcore.util.TabCompleteHelper;
import com.shardedcore.util.Text;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CoinflipModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private static final String STATE_TOGGLE = "coinflip-enabled";

    private CoinflipDatabase database;
    private CoinflipGuiHandler guiHandler;
    private final Map<UUID, BukkitTask> animations = new ConcurrentHashMap<>();

    public CoinflipModule(ShardedCore plugin) {
        super(plugin, "coinflip");
    }

    CoinflipDatabase database() {
        return database;
    }

    EconomyService economy() {
        EconomyModule module = EconomyModule.get();
        return module == null ? null : module.service();
    }

    @Override
    public void enable() {
        try {
            database = new CoinflipDatabase(plugin, moduleFolder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open coinflip database", e);
        }
        guiHandler = new CoinflipGuiHandler(this);
        registerListener(this);
        registerCommand("cf", this);
    }

    @Override
    public void disable() {
        for (BukkitTask task : animations.values()) task.cancel();
        animations.clear();
        if (database != null) database.close();
        database = null;
        guiHandler = null;
        cleanup();
    }

    boolean isAccepting(UUID uuid) {
        return plugin.stateStore().getBool(uuid, STATE_TOGGLE, true);
    }

    String guiRaw(String key, String... replacements) {
        return Text.apply(config.getString("gui." + key, ""), replacements);
    }

    List<String> guiRawList(String key, String... replacements) {
        List<String> lines = new ArrayList<>(config.getStringList("gui." + key));
        if (lines.isEmpty()) {
            String single = config.getString("gui." + key);
            if (single != null && !single.isEmpty()) lines.add(single);
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.apply(line, replacements));
        }
        return out;
    }

    String messageRaw(String key, String... replacements) {
        return raw(key, replacements);
    }

    String formatMoney(long amount) {
        EconomyModule module = EconomyModule.get();
        if (module != null) {
            return module.formatBalance(amount);
        }
        return Numbers.format(amount);
    }

    void promptCreate(Player player) {
        send(player, "create-prompt");
    }

    void joinGame(Player challenger, CoinflipDatabase.OpenGame game) {
        if (!isAccepting(challenger.getUniqueId())) {
            send(challenger, "toggle-disabled-self");
            return;
        }
        if (game.creator().equals(challenger.getUniqueId())) {
            send(challenger, "cannot-join-own");
            return;
        }
        Player creator = Bukkit.getPlayer(game.creator());
        if (creator == null || !creator.isOnline()) {
            database.deleteGame(game.creator());
            EconomyService eco = economy();
            if (eco != null) eco.add(game.creator(), game.amount());
            send(challenger, "creator-offline");
            guiHandler.openMain(challenger);
            return;
        }
        if (!isAccepting(game.creator())) {
            send(challenger, "toggle-disabled-other", "player", creator.getName());
            return;
        }
        EconomyService eco = economy();
        if (eco == null) {
            send(challenger, "economy-unavailable");
            return;
        }
        long amount = game.amount();
        if (eco.getBalance(challenger.getUniqueId()) < amount) {
            send(challenger, "not-enough", "amount", formatMoney(amount));
            return;
        }
        if (!eco.take(challenger.getUniqueId(), amount)) {
            send(challenger, "not-enough", "amount", formatMoney(amount));
            return;
        }
        database.deleteGame(game.creator());
        runAnimation(challenger, creator, amount);
    }

    private void createGame(Player player, long amount) {
        if (!isAccepting(player.getUniqueId())) {
            send(player, "toggle-disabled-self");
            return;
        }
        EconomyService eco = economy();
        if (eco == null) {
            send(player, "economy-unavailable");
            return;
        }
        long min = config.getLong("limits.min", 100L);
        long max = config.getLong("limits.max", 1_000_000L);
        if (amount < min || amount > max) {
            send(player, "invalid-amount", "min", formatMoney(min), "max", formatMoney(max));
            return;
        }
        if (eco.getBalance(player.getUniqueId()) < amount) {
            send(player, "not-enough", "amount", formatMoney(amount));
            return;
        }
        if (!eco.take(player.getUniqueId(), amount)) {
            send(player, "not-enough", "amount", formatMoney(amount));
            return;
        }
        CoinflipDatabase.OpenGame existing = database.getGame(player.getUniqueId());
        if (existing != null) {
            eco.add(player.getUniqueId(), existing.amount());
            database.deleteGame(player.getUniqueId());
        }
        CoinflipDatabase.OpenGame game = database.createGame(player.getUniqueId(), amount);
        if (game == null) {
            eco.add(player.getUniqueId(), amount);
            send(player, "create-failed");
            return;
        }
        send(player, "created", "amount", formatMoney(amount));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    private void deleteGame(Player player) {
        CoinflipDatabase.OpenGame game = database.getGame(player.getUniqueId());
        if (game == null) {
            send(player, "no-game");
            return;
        }
        database.deleteGame(player.getUniqueId());
        EconomyService eco = economy();
        if (eco != null) eco.add(player.getUniqueId(), game.amount());
        send(player, "deleted", "amount", formatMoney(game.amount()));
    }

    private void toggle(Player player) {
        boolean enabled = !isAccepting(player.getUniqueId());
        plugin.stateStore().setBool(player.getUniqueId(), STATE_TOGGLE, enabled);
        send(player, enabled ? "toggle-enabled" : "toggle-disabled");
    }

    private void runAnimation(Player challenger, Player creator, long amount) {
        CoinflipDatabase.OpenGame pseudo = new CoinflipDatabase.OpenGame(
                0, creator.getUniqueId(), amount, System.currentTimeMillis());
        guiHandler.openAnimation(challenger, pseudo);

        int ticks = Math.max(1, config.getInt("animation.ticks", 40));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = ticks;
            boolean flip = new Random().nextBoolean();

            @Override
            public void run() {
                if (!challenger.isOnline() || !creator.isOnline()) {
                    refundBoth(challenger, creator, amount);
                    cancelAnimation(challenger.getUniqueId());
                    return;
                }
                if (left <= 0) {
                    cancelAnimation(challenger.getUniqueId());
                    boolean creatorWins = new Random().nextBoolean();
                    UUID winner = creatorWins ? creator.getUniqueId() : challenger.getUniqueId();
                    UUID loser = creatorWins ? challenger.getUniqueId() : creator.getUniqueId();
                    finishFlip(winner, loser, amount, creatorWins);
                    return;
                }
                String bar = config.getString("animation.actionbar", "&#FFD700&lCOINFLIP &8▷ &fFlipping...");
                challenger.sendActionBar(Text.c(bar));
                creator.sendActionBar(Text.c(bar));
                challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, flip ? 1.2f : 0.8f);
                left--;
                flip = !flip;
            }
        }, 0L, 2L);
        animations.put(challenger.getUniqueId(), task);
    }

    private void finishFlip(UUID winner, UUID loser, long amount, boolean creatorWon) {
        EconomyService eco = economy();
        long payout = amount * 2L;
        if (eco != null) eco.add(winner, payout);
        database.recordHistory(winner, loser, amount, creatorWon);

        Player winPlayer = Bukkit.getPlayer(winner);
        Player losePlayer = Bukkit.getPlayer(loser);
        if (winPlayer != null) {
            winPlayer.closeInventory();
            send(winPlayer, "won", "amount", formatMoney(payout));
            winPlayer.playSound(winPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
        if (losePlayer != null) {
            losePlayer.closeInventory();
            send(losePlayer, "lost", "amount", formatMoney(amount));
        }
    }

    private void refundBoth(Player a, Player b, long amount) {
        EconomyService eco = economy();
        if (eco == null) return;
        eco.add(a.getUniqueId(), amount);
        eco.add(b.getUniqueId(), amount);
    }

    private void cancelAnimation(UUID uuid) {
        BukkitTask task = animations.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CoinflipGuiHandler.CoinflipGuiHolder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), CoinflipGuiHandler.CoinflipGuiHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelAnimation(uuid);
        CoinflipDatabase.OpenGame game = database.getGame(uuid);
        if (game != null) {
            database.deleteGame(uuid);
            EconomyService eco = economy();
            if (eco != null) eco.add(uuid, game.amount());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.coinflip.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            guiHandler.openMain(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    send(player, "create-usage");
                    yield true;
                }
                yield handleCreateAmount(player, args[1]);
            }
            case "delete" -> {
                deleteGame(player);
                yield true;
            }
            case "toggle" -> {
                toggle(player);
                yield true;
            }
            case "history" -> {
                if (args.length >= 2 && sender.hasPermission("shardedcore.coinflip.history.others")) {
                    OfflinePlayer target = OfflinePlayers.resolve(args[1]);
                    if (target == null) {
                        send(player, "player-not-found", "player", args[1]);
                    } else {
                        openHistoryOther(player, target.getUniqueId());
                    }
                } else {
                    guiHandler.openHistory(player, 0);
                }
                yield true;
            }
            case "stats" -> {
                guiHandler.openStats(player);
                yield true;
            }
            default -> {
                send(player, "usage");
                yield true;
            }
        };
    }

    private boolean handleCreateAmount(Player player, String raw) {
        long amount = Numbers.parseAmount(raw);
        if (amount <= 0) {
            send(player, "invalid-amount-number");
            return true;
        }
        createGame(player, amount);
        return true;
    }

    private void openHistoryOther(Player viewer, UUID target) {
        CoinflipDatabase.Stats stats = database.stats(target);
        send(viewer, "history-other-header", "player", OfflinePlayers.name(target));
        send(viewer, "history-other-stats",
                "wins", String.valueOf(stats.wins()),
                "losses", String.valueOf(stats.losses()),
                "won", formatMoney(stats.won()),
                "lost", formatMoney(stats.lost()));
        for (CoinflipDatabase.HistoryEntry entry : database.history(target, config.getInt("history.command-limit", 10))) {
            boolean won = target.equals(entry.winner());
            send(viewer, "history-other-line",
                    "result", won ? messageRaw("result-win") : messageRaw("result-loss"),
                    "opponent", OfflinePlayers.name(won ? entry.loser() : entry.winner()),
                    "amount", formatMoney(entry.amount()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("shardedcore.coinflip.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompleteHelper.filter(List.of("create", "delete", "toggle", "history", "stats"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("history")
                && sender.hasPermission("shardedcore.coinflip.history.others")) {
            return OfflinePlayers.onlinePlayers(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return TabCompleteHelper.filter(List.of("100", "1k", "10k", "100k", "1m"), args[1]);
        }
        return List.of();
    }
}
