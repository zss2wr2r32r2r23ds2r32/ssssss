package com.shardedcore.modules.commands;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private volatile Set<String> allowed = Set.of();
    private BukkitTask scheduled;

    public CommandsModule(ShardedCore plugin) {
        super(plugin, "commands");
    }

    @Override
    public void enable() {
        rebuildWhitelist();
        registerCommand("discord", this);
        registerCommand("store", this);
        registerCommand("apply", this);
        registerCommand("media", this);
        registerCommand("survival", this);
        registerCommand("events", this);
        registerCommand("diasmp", this);
        registerCommand("dev", this);
        registerCommand("server", this);
        registerCommand("patron", this);
        registerListener(this);
        String channel = config.getString("servers.channel", "BungeeCord");
        if (channel != null && !channel.isBlank()) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        }
        startScheduled();
    }

    @Override
    public void disable() {
        if (scheduled != null) scheduled.cancel();
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        rebuildWhitelist();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        startScheduled();
    }

    private void startScheduled() {
        if (scheduled != null) scheduled.cancel();
        if (!config.getBoolean("scheduled.enabled", true)) return;
        long minutes = Math.max(1, config.getLong("scheduled.interval-minutes", 15));
        String command = config.getString("scheduled.command", "asyncarenas reset warzone");
        if (command == null || command.isBlank()) return;
        long ticks = minutes * 20L * 60L;
        scheduled = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), ticks, ticks);
    }

    private void rebuildWhitelist() {
        Set<String> next = new HashSet<>();
        for (String name : config.getStringList("whitelist.commands")) {
            if (name == null || name.isBlank()) continue;
            String lower = name.toLowerCase(Locale.ROOT).trim();
            if (lower.startsWith("/")) lower = lower.substring(1);
            if (lower.contains(":")) lower = lower.substring(lower.indexOf(':') + 1);
            next.add(lower);
        }
        allowed = next;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("survival") || name.equals("events") || name.equals("event")
                || name.equals("diasmp") || name.equals("dev") || name.equals("server")) {
            return serverCommand(sender, name, args);
        }
        if (name.equals("patron")) {
            if (sender instanceof Player player) openPatron(player);
            return true;
        }
        if (name.equals("media")) {
            if (!(sender instanceof Player player)) {
                show(sender, "Media");
                return true;
            }
            if (config.getBoolean("media.menu.enabled", true)) {
                openMedia(player);
            } else {
                show(player, "Media");
            }
            return true;
        }
        String key = switch (name) {
            case "store", "webstore", "website" -> "Store";
            case "apply" -> "Apply";
            default -> "Discord";
        };
        show(sender, key);
        return true;
    }

    private void show(CommandSender sender, String key) {
        ConfigurationSection section = section(key);
        if (section == null) {
            sendRaw(sender, "&#FF0000&lERROR &8▷ &fMissing " + key + " in commands/config.yml");
            return;
        }
        sendLines(sender, section.getStringList("message"), section.getString("url", ""));
        if (sender instanceof Player player) {
            Sounds.play(player, section.getString("sound", ""), 1f, 1.2f);
        }
    }

    private ConfigurationSection section(String name) {
        if (config.isConfigurationSection(name)) return config.getConfigurationSection(name);
        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase(name)) return config.getConfigurationSection(key);
        }
        return null;
    }

    private void openMedia(Player player) {
        ConfigurationSection menu = config.getConfigurationSection("media.menu");
        if (menu == null) {
            show(player, "Media");
            return;
        }
        if (!player.hasPermission(menu.getString("open_permission", "shardedcore.command.media"))) {
            sendRaw(player, cfg("whitelist.unknown", "&#00A2FF&lCORE &8▷ &fYou do not have Permission."));
            return;
        }
        Menus.Menu gui = plugin.menus().create(player, menu.getString("menu_title", menu.getString("title", "&8Media")),
                Math.max(1, menu.getInt("size", 27) / 9));
        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(id);
                if (item == null) continue;
                ItemStack stack = Items.fromSection(item, player);
                if (id.equals("filler") || item.isList("slots")) {
                    for (int slot : item.getIntegerList("slots")) {
                        gui.set(slot, stack);
                    }
                    continue;
                }
                int slot = item.getInt("slot", 0);
                List<String> clicks = item.getStringList("left_click_commands");
                gui.set(slot, stack, event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    runClicks(player, clicks);
                });
            }
        }
        plugin.menus().open(player, gui);
        Sounds.play(player, menu.getString("open-sound", "block.note_block.pling"), 0.7f, 1.2f);
    }

    private void runClicks(Player player, List<String> clicks) {
        if (clicks == null || clicks.isEmpty()) {
            show(player, "Media");
            return;
        }
        for (String raw : clicks) {
            if (raw == null || raw.isBlank()) continue;
            String line = raw.trim();
            if (showsMedia(line)) {
                show(player, "Media");
                continue;
            }
            if (line.startsWith("[player] ")) {
                player.performCommand(line.substring(9));
            } else if (line.startsWith("[console] ")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring(10).replace("%player%", player.getName()));
            } else if (line.startsWith("[message] ")) {
                sendRaw(player, line.substring(10));
            } else {
                show(player, "Media");
            }
        }
    }

    private boolean showsMedia(String line) {
        String value = line;
        if (value.startsWith("[player] ") || value.startsWith("[message] ")) {
            value = value.substring(value.indexOf(' ') + 1).trim();
        }
        return value.equalsIgnoreCase("discord")
                || value.equalsIgnoreCase("media")
                || value.equalsIgnoreCase("show-media")
                || value.equalsIgnoreCase("show_media");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSend(PlayerCommandSendEvent event) {
        if (!config.getBoolean("whitelist.enabled", true)) return;
        if (event.getPlayer().hasPermission(cfg("whitelist.bypass-permission", "shardedcore.command.bypass"))) return;
        Set<String> allow = allowed;
        if (allow.isEmpty()) return;
        event.getCommands().removeIf(command -> {
            String name = command.toLowerCase(Locale.ROOT);
            if (name.contains(":")) return true;
            return !allow.contains(name);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnknown(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("whitelist.enabled", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission(cfg("whitelist.bypass-permission", "shardedcore.command.bypass"))) return;
        Set<String> allow = allowed;
        if (allow.isEmpty()) return;
        String raw = event.getMessage();
        if (raw.length() < 2 || raw.charAt(0) != '/') return;
        String name = raw.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        if (proxyCommand(name)) return;
        Command command = Bukkit.getCommandMap().getCommand(name);
        if (command != null) return;
        event.setCancelled(true);
        player.sendActionBar(ColorUtil.parse(cfg("whitelist.unknown",
                "&#00A2FF&lCORE &8▷ &fYou do not have Permission.")));
        Sounds.play(player, config.getConfigurationSection("whitelist.sound"));
    }

    private boolean serverCommand(CommandSender sender, String name, String[] args) {
        if (!(sender instanceof Player player)) {
            sendRaw(sender, "&#FF0000&lERROR &8▷ &fOnly a player can do that.");
            return true;
        }
        String server = switch (name) {
            case "survival" -> config.getString("servers.survival", "survival");
            case "events", "event" -> config.getString("servers.events", "events");
            case "diasmp" -> config.getString("servers.diasmp", "diasmp");
            case "dev" -> {
                if (args.length > 0 && args[0].equals("1")) yield config.getString("servers.dev1", "dev1");
                if (args.length > 0 && args[0].equals("2")) yield config.getString("servers.dev2", "dev2");
                yield config.getString("servers.dev", "dev");
            }
            case "server" -> {
                if (args.length == 0) {
                    sendRaw(player, cfg("servers.usage", "&#00A2FF&lCORE &8▷ &fUse /server <name>"));
                    yield "";
                }
                yield serverName(args[0]);
            }
            default -> name;
        };
        if (server == null || server.isBlank()) return true;
        connect(player, server);
        return true;
    }

    private boolean proxyCommand(String name) {
        List<String> extra = config.getStringList("whitelist.proxy-commands");
        if (extra.isEmpty()) extra = List.of("server", "hub", "lobby", "queue");
        return extra.stream().anyMatch(value -> value != null && value.equalsIgnoreCase(name));
    }

    private String serverName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String key = raw.toLowerCase(Locale.ROOT);
        String mapped = config.getString("servers." + key, "");
        if (mapped != null && !mapped.isBlank() && !key.equals("channel")) return mapped;
        return raw;
    }

    private void connect(Player player, String server) {
        if (server == null || server.isBlank()) return;
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, config.getString("servers.channel", "BungeeCord"), bytes.toByteArray());
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not send " + player.getName() + " to " + server + ": " + ex.getMessage());
        }
    }

    private void openPatron(Player player) {
        ConfigurationSection gui = config.getConfigurationSection("patron");
        Sounds.play(player, gui == null ? "item.book.page_turn" : gui.getString("open-sound", "item.book.page_turn"), 1f, 1f);
        String spent = spent(player);
        int rows = gui == null ? 4 : Math.max(1, gui.getInt("rows", 4));
        Menus.Menu menu = plugin.menus().create(player, gui == null ? "🔥 Patron" : gui.getString("title", "🔥 Patron"), rows);
        ItemStack fill = Items.named(Sounds.material(gui == null ? "GRAY_STAINED_GLASS_PANE"
                : gui.getString("filler.material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE), " ", List.of());
        for (int slot = 0; slot < rows * 9; slot++) menu.set(slot, fill);
        placePatron(menu, player, gui, "tier-1", 11, Material.PINK_HARNESS, spent);
        placePatron(menu, player, gui, "tier-2", 13, Material.LIGHT_BLUE_HARNESS, spent);
        placePatron(menu, player, gui, "tier-3", 15, Material.RED_HARNESS, spent);
        ConfigurationSection close = gui == null ? null : gui.getConfigurationSection("close");
        int closeSlot = close == null ? 31 : close.getInt("slot", 31);
        menu.set(closeSlot, Items.fromSection(close, player), event -> {
            event.setCancelled(true);
            Sounds.play(player, gui == null ? "block.barrel.close" : gui.getString("close-sound", "block.barrel.close"), 1f, 1f);
            player.closeInventory();
        });
        if (close == null) {
            menu.set(31, Items.named(Material.FLOWER_BANNER_PATTERN, "&#FF0000&lCLOSE", List.of(
                    "&8Description", "", "&#FF0000Information:", "&#FF0000| &fClose this GUI by Clicking", "",
                    GuiButtons.clickFooter("To Close"))), event -> {
                event.setCancelled(true);
                Sounds.play(player, "block.barrel.close", 1f, 1f);
                player.closeInventory();
            });
        }
        plugin.menus().open(player, menu);
    }

    private void placePatron(Menus.Menu menu, Player player, ConfigurationSection gui, String id, int fallback,
                             Material material, String spent) {
        ConfigurationSection section = gui == null ? null : gui.getConfigurationSection(id);
        int slot = section == null ? fallback : section.getInt("slot", fallback);
        ItemStack item = section == null
                ? Items.named(material, "&#F97E9C&lPATRON", List.of("&7You have spent " + spent))
                : Items.fromSection(section, player, "spent", spent);
        menu.set(slot, item, event -> {
            event.setCancelled(true);
            Sounds.play(player, "ui.button.click", 1f, 1f);
            player.performCommand(section == null ? "store" : section.getString("command", "store"));
        });
    }

    private String spent(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%ctd_player_spent_alltime%");
        }
        return "0";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("dev") && args.length == 1) {
            return Tabs.filter(List.of("1", "2"), args[0]);
        }
        if (command.getName().equalsIgnoreCase("server") && args.length == 1) {
            List<String> names = new ArrayList<>();
            ConfigurationSection servers = config.getConfigurationSection("servers");
            if (servers != null) {
                for (String key : servers.getKeys(false)) {
                    if (key.equalsIgnoreCase("channel") || key.equalsIgnoreCase("usage")) continue;
                    names.add(key);
                }
            }
            return Tabs.filter(names, args[0]);
        }
        return List.of();
    }
}
