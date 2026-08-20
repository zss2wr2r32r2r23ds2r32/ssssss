package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokensModule extends Module implements CommandExecutor, TabCompleter {

    private TokenDatabase database;
    private TokenService service;
    private BukkitTask playtimeTask;
    private final Map<UUID, Long> onlineSince = new ConcurrentHashMap<>();

    private static final String PLAYTIME_LAST_GRANT = "tokens-hourly-last-grant";

    public TokensModule(ShardedCore plugin) {
        super(plugin, "tokens");
    }

    public TokenService service() {
        return service;
    }

    public TokenDatabase database() {
        return database;
    }

    public String tokenPrefix() {
        return messagePrefix();
    }

    @Override
    protected void onEnable() {
        try {
            database = new TokenDatabase(plugin, moduleFolder());
            service = new TokenService(database);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open token database", e);
        }

        plugin.gui().setNoPermissionMessage(raw("no-permission"));
        File menusFolder = new File(moduleFolder(), "menus");
        copyDefaultMenus(menusFolder);
        plugin.gui().loadFolder(menusFolder, "");

        registerCommand("bal", this);
        registerCommand("tokens", this);
        registerCommand("tokenshop", this);

        startPlaytimeRewards();
    }

    private void startPlaytimeRewards() {
        if (!config.getBoolean("playtime-reward.enabled", true)) return;
        long intervalTicks = Math.max(20L, config.getLong("playtime-reward.check-interval-seconds", 60L) * 20L);
        playtimeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPlaytimeRewards, intervalTicks, intervalTicks);
    }

    private void tickPlaytimeRewards() {
        if (service == null) return;
        long amount = config.getLong("playtime-reward.amount", 50L);
        long intervalMs = config.getLong("playtime-reward.interval-minutes", 60L) * 60_000L;
        if (amount <= 0 || intervalMs <= 0) return;
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long last = plugin.stateStore().getLong(uuid, PLAYTIME_LAST_GRANT, onlineSince.getOrDefault(uuid, now));
            if (now - last < intervalMs) continue;
            service.give(uuid, amount);
            plugin.stateStore().setLong(uuid, PLAYTIME_LAST_GRANT, now);
            send(player, "playtime-reward", "%amount%", String.valueOf(amount));
        }
    }

    @org.bukkit.event.EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.getBoolean("playtime-reward.enabled", true)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        onlineSince.put(uuid, now);
        if (plugin.stateStore().getLong(uuid, PLAYTIME_LAST_GRANT, 0L) == 0L) {
            plugin.stateStore().setLong(uuid, PLAYTIME_LAST_GRANT, now);
        }
    }

    @org.bukkit.event.EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlineSince.remove(event.getPlayer().getUniqueId());
    }

    private void copyDefaultMenus(File folder) {
        folder.mkdirs();
        for (String menu : List.of("mainmenu", "glow", "keys", "cosmetics", "gradients", "chatcolors", "tags", "backpack")) {
            String path = "modules/tokens/menus/" + menu + ".yml";
            ConfigSync.sync(plugin, new File(folder, menu + ".yml"), path);
        }
    }

    @Override
    protected void onDisable() {
        if (playtimeTask != null) playtimeTask.cancel();
        playtimeTask = null;
        onlineSince.clear();
        if (database != null) database.close();
        database = null;
        service = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> {
                if (args.length == 0) {
                    if (!(sender instanceof Player player)) {
                        send(sender, "players-only");
                        return true;
                    }
                    long bal = service.getBalance(player.getUniqueId());
                    send(player, "balance-self", "%amount%", String.valueOf(bal), "%formatted%", Numbers.format(bal));
                    return true;
                }
                OfflinePlayer target = service.resolve(args[0]);
                long bal = service.getBalance(target.getUniqueId());
                send(sender, "balance-other", "%player%", name(target),
                        "%amount%", String.valueOf(bal), "%formatted%", Numbers.format(bal));
                return true;
            }
            case "tokenshop" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "players-only");
                    return true;
                }
                if (!player.hasPermission("sharded.tokenshop.use")) {
                    send(player, "no-permission");
                    return true;
                }
                plugin.gui().open(player, config.getString("main-menu", "mainmenu"));
            }
            case "tokens" -> handleTokensAdmin(sender, args);
        }
        return true;
    }

    private void handleTokensAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "tokens-usage");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "give-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                long amount = parseAmount(args[2]);
                service.give(target.getUniqueId(), amount);
                send(sender, "given", "%player%", name(target), "%amount%", String.valueOf(amount));
            }
            case "set" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "set-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                long amount = parseAmount(args[2]);
                service.setBalance(target.getUniqueId(), amount);
                send(sender, "set", "%player%", name(target), "%amount%", String.valueOf(amount));
            }
            case "remove", "take" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "remove-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                long amount = parseAmount(args[2]);
                service.take(target.getUniqueId(), amount);
                send(sender, "removed", "%player%", name(target), "%amount%", String.valueOf(amount));
            }
            case "reset" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 2) {
                    send(sender, "reset-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                service.reset(target.getUniqueId());
                send(sender, "reset", "%player%", name(target));
            }
            case "giveall" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 2) {
                    send(sender, "giveall-usage");
                    return;
                }
                long amount = parseAmount(args[1]);
                int count = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    service.give(online.getUniqueId(), amount);
                    count++;
                }
                send(sender, "giveall", "%count%", String.valueOf(count), "%amount%", String.valueOf(amount));
            }
            default -> send(sender, "tokens-usage");
        }
    }

    private long parseAmount(String raw) {
        return Numbers.parseAmount(raw);
    }

    private String name(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("bal")) {
            if (args.length == 1) return TabCompleteHelper.onlinePlayers(args[0]);
            return List.of();
        }
        if (!cmd.equals("tokens") || !sender.hasPermission("sharded.tokens.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "give", "set", "remove", "take", "reset", "giveall");
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("giveall")) {
            return TabCompleteHelper.knownPlayers(args[1]);
        }
        if (args.length == 3 && !args[0].equalsIgnoreCase("reset") && !args[0].equalsIgnoreCase("giveall")) {
            return TabCompleteHelper.filter(args[2], "1", "100", "1k", "10k", "100k", "1m", "10m");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveall")) {
            return TabCompleteHelper.filter(args[1], "1k", "10k", "100k", "1m", "10m");
        }
        return List.of();
    }
}
