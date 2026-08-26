package com.shardedcore.modules.tpa;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, List<Request>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> teleporting = new ConcurrentHashMap<>();

    public TpaModule(ShardedCore plugin) {
        super(plugin, "tpa");
    }

    @Override
    public void enable() {
        registerCommand("tpa", this);
        registerCommand("tpahere", this);
        registerCommand("tpaccept", this);
        registerCommand("tpacancel", this);
        registerListener(this);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    @Override
    public void disable() {
        teleporting.values().forEach(BukkitTask::cancel);
        teleporting.clear();
        incoming.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.players-only");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> openRequest(player, args, false);
            case "tpahere" -> openRequest(player, args, true);
            case "tpaccept", "tpyes" -> accept(player, args);
            case "tpacancel", "tpdeny", "tpadeny" -> cancel(player);
            default -> true;
        };
    }

    private boolean openRequest(Player player, String[] args, boolean here) {
        if (args.length == 0) {
            send(player, here ? "messages.usage-here" : "messages.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(player, "messages.offline");
            return true;
        }
        if (target.equals(player)) {
            send(player, "messages.self");
            return true;
        }
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        if (settings != null) {
            if (!settings.tpa(target) || (here && !settings.tpaHere(target))) {
                send(player, "messages.disabled");
                return true;
            }
            if (settings.tpAuto(target)) {
                sendNow(player, target, here);
                return true;
            }
        }
        Menus.Menu menu = plugin.menus().create(player, cfg("title", "&8Teleport Request"), 3);
        menu.fill(Items.named(Sounds.material(cfg("filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                cfg("filler.name", " "), List.of()));
        menu.set(10, Items.named(Material.RED_CANDLE, "&#FF0000&lCANCEL &#FF0000Request", List.of(
                "&8Description", "",
                "&#FF0000Information:",
                "&#FF0000| &fClick Here to",
                "&#FF0000| &fClose this Menu",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Close"
        )), event -> {
            event.setCancelled(true);
            player.closeInventory();
        });
        World.Environment env = target.getWorld().getEnvironment();
        Material worldItem = switch (env) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.MOSS_BLOCK;
        };
        String color = switch (env) {
            case NETHER -> "&#FF0000";
            case THE_END -> "&#FCFF00";
            default -> "&#9FFF00";
        };
        menu.set(12, Items.named(worldItem, color + "&lPLAYERS WORLD", List.of(
                "&8Description", "",
                color + "Information:",
                color + "| &fThis is the current",
                color + "| &fWorld this player is in",
                "",
                color + "▷ &fWorld: " + color + target.getWorld().getName()
        )));
        menu.set(13, Items.head(target, "&#FF0072&lPLAYERS NAME", List.of(
                "&8Description", "",
                "&#FF0072Information:",
                "&#FF0072| &fThe Name of",
                "&#FF0072| &fThe player you will teleport to",
                "",
                "&#FF0072▷ &fName: &#FF0072" + target.getName()
        )));
        String region = region(target);
        menu.set(14, Items.named(Material.RECOVERY_COMPASS, "&#00A2FF&lPLAYERS REGION", List.of(
                "&8Description", "",
                "&#00A2FFInformation:",
                "&#00A2FF| &fThe Region of",
                "&#00A2FF| &fThe player",
                "",
                "&#00A2FF▷ &fRegion: &#00A2FF" + region
        )));
        menu.set(16, Items.named(Material.LIME_CANDLE, "&#8AFF00&lSEND &#8AFF00Request", List.of(
                "&8Description", "",
                "&#8AFF00Information:",
                "&#8AFF00| &fClick Here to",
                "&#8AFF00| &fSend Teleport Request",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Request"
        )), event -> {
            event.setCancelled(true);
            player.closeInventory();
            sendNow(player, target, here);
        });
        plugin.menus().open(player, menu);
        return true;
    }

    private void sendNow(Player from, Player to, boolean here) {
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (combat != null && (combat.tagged(from) || combat.tagged(to))) {
            send(from, "messages.combat");
            return;
        }
        incoming.computeIfAbsent(to.getUniqueId(), ignored -> new ArrayList<>())
                .add(new Request(from.getUniqueId(), to.getUniqueId(), here, System.currentTimeMillis()));
        send(from, "messages.sent", "player", to.getName());
        send(to, here ? "messages.here-received" : "messages.received", "player", from.getName());
    }

    private boolean accept(Player player, String[] args) {
        List<Request> list = incoming.getOrDefault(player.getUniqueId(), List.of());
        Request request = null;
        if (args.length > 0) {
            for (Request candidate : list) {
                Player from = Bukkit.getPlayer(candidate.from);
                if (from != null && from.getName().equalsIgnoreCase(args[0])) request = candidate;
            }
        } else if (!list.isEmpty()) {
            request = list.get(list.size() - 1);
        }
        if (request == null) {
            send(player, "messages.none");
            return true;
        }
        incoming.get(player.getUniqueId()).remove(request);
        Player from = Bukkit.getPlayer(request.from);
        if (from == null) {
            send(player, "messages.offline");
            return true;
        }
        Player mover = request.here ? player : from;
        org.bukkit.Location dest = request.here ? from.getLocation() : player.getLocation();
        startTeleport(mover, dest);
        send(player, "messages.accepted");
        send(from, "messages.accepted");
        return true;
    }

    private boolean cancel(Player player) {
        incoming.remove(player.getUniqueId());
        incoming.values().forEach(list -> list.removeIf(request -> request.from.equals(player.getUniqueId())));
        send(player, "messages.denied");
        return true;
    }

    private void startTeleport(Player player, org.bukkit.Location dest) {
        int seconds = config.getInt("countdown-seconds", 5);
        int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stop(player.getUniqueId());
                return;
            }
            if (left[0] <= 0) {
                stop(player.getUniqueId());
                player.teleportAsync(dest);
                return;
            }
            player.sendActionBar(ColorUtil.parse(cfg("messages.teleporting", "").replace("%seconds%", String.valueOf(left[0]))));
            left[0]--;
        }, 0L, 20L);
        teleporting.put(player.getUniqueId(), task);
    }

    private void stop(UUID uuid) {
        BukkitTask task = teleporting.remove(uuid);
        if (task != null) task.cancel();
    }

    private void expire() {
        long life = config.getLong("expire-seconds", 60) * 1000L;
        long now = System.currentTimeMillis();
        incoming.values().forEach(list -> {
            Iterator<Request> iterator = list.iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().at > life) iterator.remove();
            }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!config.getBoolean("cancel-on-move", true)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!teleporting.containsKey(event.getPlayer().getUniqueId())) return;
        stop(event.getPlayer().getUniqueId());
        sendBar(event.getPlayer(), "messages.cancelled-move");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
        incoming.remove(event.getPlayer().getUniqueId());
    }

    private String region(Player player) {
        String locale = player.locale().toString().toLowerCase(Locale.ROOT).replace('-', '_');
        ConfigurationSection map = config.getConfigurationSection("regions");
        if (map != null) {
            String exact = map.getString(locale);
            if (exact != null && !exact.isBlank()) return exact;
            int under = locale.indexOf('_');
            if (under > 0) {
                String lang = map.getString(locale.substring(0, under));
                if (lang != null && !lang.isBlank()) return lang;
            }
        }
        if (locale.startsWith("en_us") || locale.startsWith("en_ca") || locale.startsWith("es_mx")
                || locale.startsWith("es_us") || locale.startsWith("fr_ca")) return "NA";
        if (locale.startsWith("pt_br") || locale.startsWith("es_ar") || locale.startsWith("es_cl")
                || locale.startsWith("es_co") || locale.startsWith("es_pe")) return "SA";
        if (locale.startsWith("ja") || locale.startsWith("ko") || locale.startsWith("zh")
                || locale.startsWith("th") || locale.startsWith("vi") || locale.startsWith("hi")) return "AS";
        if (locale.startsWith("en_au") || locale.startsWith("en_nz")) return "OC";
        if (locale.startsWith("en_za") || locale.startsWith("af") || locale.startsWith("ar")) return "AF";
        return cfg("regions.default", "EU");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Tabs.players(args[0]) : List.of();
    }

    private record Request(UUID from, UUID to, boolean here, long at) {
    }
}
