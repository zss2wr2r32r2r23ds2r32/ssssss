package com.shardedcore.modules.settings;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SettingsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    public static final String CHAT = "chat";
    public static final String MSG = "msg";
    public static final String LIVE = "live";
    public static final String TPA = "tpa";
    public static final String TPAHERE = "tpahere";
    public static final String JOIN = "joinleave";
    public static final String MOBS = "mobs";
    public static final String DEATH = "death";
    public static final String PAY = "pay";
    public static final String NV = "nightvision";
    public static final String ORDERS = "orders";
    public static final String BOSSBAR = "bossbar";
    public static final String SCOREBOARD = "scoreboard";
    public static final String TPAUTO = "tpauto";
    public static final String CF = "coinflip";
    public static final String CRYSTAL = "crystal";

    private static final List<String> ORDER = List.of(
            CHAT, MSG, LIVE, TPA, TPAHERE, JOIN, MOBS, DEATH, PAY, NV, ORDERS, BOSSBAR, SCOREBOARD, TPAUTO, CF, CRYSTAL
    );

    private final Map<UUID, UUID> replies = new ConcurrentHashMap<>();

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    public void enable() {
        registerListener(this);
        registerCommand("settings", this);
        registerCommand("chattoggle", this);
        registerCommand("msgtoggle", this);
        registerCommand("livetoggle", this);
        registerCommand("tpatoggle", this);
        registerCommand("tpaheretoggle", this);
        registerCommand("joinleavetoggle", this);
        registerCommand("mobspawning", this);
        registerCommand("deathtoggle", this);
        registerCommand("paytoggle", this);
        registerCommand("nvtoggle", this);
        registerCommand("ordertoggle", this);
        registerCommand("tabbossbar", this);
        registerCommand("tabscoreboard", this);
        registerCommand("tpauto", this);
        registerCommand("crystaltoggle", this);
        registerCommand("msg", this);
        registerCommand("r", this);
        for (Player player : Bukkit.getOnlinePlayers()) applyNightVision(player);
    }

    @Override
    public void disable() {
        cleanup();
    }

    public boolean on(Player player, String key) {
        return plugin.toggles().get(player.getUniqueId(), key, config.getBoolean("defaults." + key, true));
    }

    public boolean publicChat(Player player) { return on(player, CHAT); }
    public boolean messages(Player player) { return on(player, MSG); }
    public boolean live(Player player) { return on(player, LIVE); }
    public boolean tpa(Player player) { return on(player, TPA); }
    public boolean tpaHere(Player player) { return on(player, TPAHERE); }
    public boolean joinLeave(Player player) { return on(player, JOIN); }
    public boolean death(Player player) { return on(player, DEATH); }
    public boolean pay(Player player) { return on(player, PAY); }
    public boolean tpAuto(Player player) { return on(player, TPAUTO); }
    public boolean coinflip(Player player) { return on(player, CF); }
    public boolean orders(Player player) { return on(player, ORDERS); }

    public boolean flipLive(Player player) {
        return flip(player, LIVE, "live", true);
    }

    public boolean flipCoinflip(Player player) {
        return flip(player, CF, "cf", true);
    }

    private boolean flip(Player player, String key, String messageKey) {
        return flip(player, key, messageKey, false);
    }

    private boolean flip(Player player, String key, String messageKey, boolean command) {
        boolean next = plugin.toggles().flip(player.getUniqueId(), key, config.getBoolean("defaults." + key, true));
        String suffix = next ? "-on" : "-off";
        if (command) {
            String text = config.getString("command-messages." + messageKey + suffix, "");
            if (text == null || text.isBlank()) {
                text = config.getString("messages." + messageKey + suffix, "");
            }
            sendRaw(player, Text.apply(text, "prefix", commandPrefix(messageKey)));
        } else {
            send(player, "messages." + messageKey + suffix);
        }
        if (key.equals(NV)) applyNightVision(player);
        syncTab(player, key, next);
        return next;
    }

    private String commandPrefix(String messageKey) {
        String specific = config.getString("command-prefixes." + messageKey, "");
        if (specific != null && !specific.isBlank()) return specific;
        return cfg("command-prefix", "&#FF0072&lSETTINGS &7▷");
    }

    private void syncTab(Player player, String key, boolean on) {
        if (key.equals(BOSSBAR)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab bossbar " + (on ? "on" : "off") + " " + player.getName());
        }
        if (key.equals(SCOREBOARD)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard " + (on ? "on" : "off") + " " + player.getName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.players-only");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("settings") || name.equals("setting")) {
            open(player, 0);
            return true;
        }
        if (name.equals("msg") || name.equals("tell") || name.equals("whisper") || name.equals("m") || name.equals("message")) {
            return msg(player, args);
        }
        if (name.equals("r") || name.equals("reply")) {
            return reply(player, args);
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("chattoggle", CHAT);
        map.put("msgtoggle", MSG);
        map.put("livetoggle", LIVE);
        map.put("tpatoggle", TPA);
        map.put("tpaheretoggle", TPAHERE);
        map.put("joinleavetoggle", JOIN);
        map.put("jointoggle", JOIN);
        map.put("mobspawning", MOBS);
        map.put("mobtoggle", MOBS);
        map.put("deathtoggle", DEATH);
        map.put("paytoggle", PAY);
        map.put("nvtoggle", NV);
        map.put("nightvisiontoggle", NV);
        map.put("nightvision", NV);
        map.put("nv", NV);
        map.put("ordertoggle", ORDERS);
        map.put("tabbossbar", BOSSBAR);
        map.put("tabscoreboard", SCOREBOARD);
        map.put("tpauto", TPAUTO);
        map.put("crystaltoggle", CRYSTAL);
        map.put("fastcrystal", CRYSTAL);
        String key = map.get(name);
        if (key != null) {
            String msgKey = switch (key) {
                case CHAT -> "chat";
                case MSG -> "msg";
                case LIVE -> "live";
                case TPA -> "tpa";
                case TPAHERE -> "tpahere";
                case JOIN -> "join";
                case MOBS -> "mobs";
                case DEATH -> "death";
                case PAY -> "pay";
                case NV -> "nv";
                case ORDERS -> "orders";
                case BOSSBAR -> "bossbar";
                case SCOREBOARD -> "scoreboard";
                case TPAUTO -> "tpauto";
                case CF -> "cf";
                case CRYSTAL -> "crystal";
                default -> key;
            };
            flip(player, key, msgKey, true);
        }
        return true;
    }

    public void open(Player player, int page) {
        List<String> keys = visibleKeys();
        int per = Math.max(1, config.getInt("items-per-page", 7));
        int pages = Math.max(1, (keys.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, cfg("title", "&8Settings"), config.getInt("rows", 3));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int start = current * per;
        String loreColor = cfg("lore-color", "&#FF0072");
        String enabledText = cfg("enabled.text", "&#A9FF00&lENABLED");
        String disabledText = cfg("disabled.text", "&#FF0000&lDISABLED");
        List<String> defaultLore = config.getStringList("lore");
        if (defaultLore.isEmpty()) {
            defaultLore = List.of(
                    "&8Description",
                    "",
                    "%color%Information:",
                    "%color%| &fClick To",
                    "%color%| &f%description%",
                    "",
                    "%color%ℹ &fCommand: %color%%command%",
                    "%color%⚓ &fCurrently: %status%",
                    "",
                    "%click%"
            );
        }
        for (int i = 0; i < slots.length && start + i < keys.size() && i < per; i++) {
            String key = keys.get(start + i);
            ConfigurationSection entry = config.getConfigurationSection("entries." + key);
            if (entry == null) continue;
            boolean on = on(player, key);
            Material material = Sounds.material(entry.getString("material",
                    on ? cfg("enabled.material", "LIME_DYE") : cfg("disabled.material", "GRAY_DYE")), Material.PAPER);
            String name = entry.getString("name", "&#FF0072&l" + key.toUpperCase(Locale.ROOT));
            List<String> loreLines = entry.getStringList("lore");
            if (loreLines.isEmpty()) loreLines = defaultLore;
            loreLines = Text.applyList(new ArrayList<>(loreLines),
                    "color", loreColor,
                    "description", entry.getString("description", "Toggle"),
                    "command", entry.getString("command", ""),
                    "status", on ? enabledText : disabledText,
                    "click", cfg("click-line", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Toggle")
            );
            menu.set(slots[i], Items.named(material, name, loreLines), event -> {
                event.setCancelled(true);
                String msgKey = messageKey(key);
                flip(player, key, msgKey);
                open(player, current);
            });
        }
        if (current > 0) {
            menu.set(config.getInt("previous.slot", 18), Items.named(
                    Sounds.material(cfg("previous.material", "RED_STAINED_GLASS_PANE"), Material.RED_STAINED_GLASS_PANE),
                    cfg("previous.name", "&#FF0000&lPREVIOUS PAGE"),
                    Items.lore(config, "previous.lore", List.of("&7Page %page%"), "page", String.valueOf(current), "next", String.valueOf(current))
            ), event -> {
                event.setCancelled(true);
                open(player, current - 1);
            });
        }
        if (current + 1 < pages) {
            menu.set(config.getInt("next.slot", 26), Items.named(
                    Sounds.material(cfg("next.material", "LIME_STAINED_GLASS_PANE"), Material.LIME_STAINED_GLASS_PANE),
                    cfg("next.name", "&#80ee0b&lNEXT PAGE"),
                    Items.lore(config, "next.lore", List.of("&7Page %page%"), "page", String.valueOf(current + 2), "next", String.valueOf(current + 2))
            ), event -> {
                event.setCancelled(true);
                open(player, current + 1);
            });
        }
        menu.fill(Items.named(
                Sounds.material(cfg("filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                cfg("filler.name", " "),
                config.getStringList("filler.lore")
        ));
        plugin.menus().open(player, menu);
    }

    private List<String> visibleKeys() {
        List<String> keys = new ArrayList<>();
        ConfigurationSection entries = config.getConfigurationSection("entries");
        if (entries == null) return ORDER;
        for (String key : ORDER) {
            if (entries.isConfigurationSection(key)) keys.add(key);
        }
        return keys;
    }

    private String messageKey(String key) {
        return switch (key) {
            case CHAT -> "chat";
            case MSG -> "msg";
            case LIVE -> "live";
            case TPA -> "tpa";
            case TPAHERE -> "tpahere";
            case JOIN -> "join";
            case MOBS -> "mobs";
            case DEATH -> "death";
            case PAY -> "pay";
            case NV -> "nv";
            case ORDERS -> "orders";
            case BOSSBAR -> "bossbar";
            case SCOREBOARD -> "scoreboard";
            case TPAUTO -> "tpauto";
            case CF -> "cf";
            case CRYSTAL -> "crystal";
            default -> key;
        };
    }

    private boolean msg(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "messages.msg-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sendRaw(player, "&#FF0000&lERROR &7▷ &fThat player is not online.");
            return true;
        }
        if (target.equals(player)) {
            send(player, "messages.msg-self");
            return true;
        }
        if (!messages(target)) {
            send(player, "messages.msg-ignored");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        sendRaw(player, Text.apply(cfg("messages.msg-format-to", ""), "player", target.getName(), "message", text));
        sendRaw(target, Text.apply(cfg("messages.msg-format-from", ""), "player", player.getName(), "message", text));
        replies.put(target.getUniqueId(), player.getUniqueId());
        replies.put(player.getUniqueId(), target.getUniqueId());
        return true;
    }

    private boolean reply(Player player, String[] args) {
        UUID last = replies.get(player.getUniqueId());
        if (last == null) {
            send(player, "messages.reply-none");
            return true;
        }
        Player target = Bukkit.getPlayer(last);
        if (target == null) {
            send(player, "messages.reply-none");
            return true;
        }
        if (args.length == 0) {
            send(player, "messages.msg-usage");
            return true;
        }
        String[] next = new String[args.length + 1];
        next[0] = target.getName();
        System.arraycopy(args, 0, next, 1, args.length);
        return msg(player, next);
    }

    private void applyNightVision(Player player) {
        if (on(player, NV)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyNightVision(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (on(player, BOSSBAR)) {
                syncTab(player, BOSSBAR, true);
                send(player, "messages.bossbar-on");
            }
            if (on(player, SCOREBOARD)) {
                syncTab(player, SCOREBOARD, true);
                send(player, "messages.scoreboard-on");
            }
        }, 20L);
    }

    @EventHandler
    public void onCrystal(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.END_CRYSTAL) return;
        if (!on(event.getPlayer(), CRYSTAL)) return;
        Player player = event.getPlayer();
        player.setCooldown(Material.END_CRYSTAL, 0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && on(player, CRYSTAL)) player.setCooldown(Material.END_CRYSTAL, 0);
        });
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        int radius = config.getInt("mob-radius", 50);
        for (Entity nearby : event.getEntity().getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player && !on(player, MOBS)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("msg") && args.length == 1) return Tabs.players(args[0]);
        return List.of();
    }
}
