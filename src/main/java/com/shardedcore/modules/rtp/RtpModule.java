package com.shardedcore.modules.rtp;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtpModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> safeUntil = new ConcurrentHashMap<>();
    private final Set<UUID> queue = ConcurrentHashMap.newKeySet();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();
    private BukkitTask queueTask;
    private int queueDots;
    private RtpSafeSpotPool pool;

    public RtpModule(ShardedCore plugin) {
        super(plugin, "rtp");
    }

    @Override
    public void enable() {
        pool = new RtpSafeSpotPool(this);
        pool.start();
        registerCommand("rtp", this);
        registerCommand("rtpqueue", this);
        registerCommand("leave", this);
        registerListener(this);
        queueTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickQueue, 20L, 20L);
    }

    @Override
    public void disable() {
        if (pool != null) pool.shutdown();
        if (queueTask != null) queueTask.cancel();
        queue.clear();
        searching.clear();
        partners.clear();
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        cooldown.clear();
        safeUntil.clear();
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        if (pool != null) {
            pool.shutdown();
            pool = new RtpSafeSpotPool(this);
            pool.start();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sendBar(sender, "players-only");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("leave") || label.equalsIgnoreCase("leave")
                || (name.equals("rtpqueue") && args.length > 0 && args[0].equalsIgnoreCase("leave"))) {
            leaveQueue(player);
            return true;
        }
        if (name.equals("rtpqueue")) {
            toggleQueue(player);
            return true;
        }
        if (args.length == 0) {
            openMenu(player);
            return true;
        }
        begin(player, args[0]);
        return true;
    }

    private void openMenu(Player player) {
        int rows = config.getInt("menu.rows", 3);
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Random Teleport"), rows);
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        if (worlds != null) {
            for (String id : worlds.getKeys(false)) {
                ConfigurationSection dest = worlds.getConfigurationSection(id);
                if (dest == null) continue;
                World world = resolveWorld(id, dest);
                int slot = dest.getInt("slot", 0);
                menu.set(slot, worldItem(dest, world), event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    begin(player, id);
                });
            }
        }
        ConfigurationSection queueButton = config.getConfigurationSection("menu.queue");
        if (queueButton == null) queueButton = config.getConfigurationSection("menu.duel");
        if (queueButton != null) {
            menu.set(queueButton.getInt("slot", 15), Items.fromSection(queueButton, player), event -> {
                event.setCancelled(true);
                player.closeInventory();
                toggleQueue(player);
            });
        }
        menu.fill(Items.named(
                Sounds.material(cfg("menu.filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                cfg("menu.filler.name", " "),
                config.getStringList("menu.filler.lore")
        ));
        plugin.menus().open(player, menu);
        sound(player, "sounds.menu-open");
    }

    private ItemStack worldItem(ConfigurationSection dest, World world) {
        Material material = Sounds.material(dest.getString("material", "STONE"), Material.STONE);
        int players = 0;
        String border = "0";
        if (world != null) {
            players = world.getPlayers().size();
            border = Amounts.format(world.getWorldBorder().getSize() / 2);
        }
        String radius = Amounts.format(dest.getInt("radius", 0));
        String name = Text.apply(dest.getString("name", ""), "players", String.valueOf(players), "radius", radius, "border", border);
        List<String> lore = new ArrayList<>();
        for (String line : dest.getStringList("lore")) {
            lore.add(Text.apply(line, "players", String.valueOf(players), "radius", radius, "border", border));
        }
        return Items.named(material, name, lore);
    }

    void begin(Player player, String arg) {
        if (pending.containsKey(player.getUniqueId())) {
            sendBar(player, "already-teleporting");
            sound(player, "sounds.error");
            return;
        }
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (combat != null && combat.tagged(player)) {
            sendBar(player, "restricted");
            sound(player, "sounds.error");
            return;
        }
        long wait = config.getLong("cooldown-seconds", 10) * 1000L;
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait) {
            long left = (wait - (System.currentTimeMillis() - last) + 999) / 1000L;
            sendBar(player, "cooldown", "time", String.valueOf(Math.max(1, left)));
            sound(player, "sounds.error");
            return;
        }
        double cost = Amounts.parse(String.valueOf(config.get("cost", 0)));
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (cost > 0 && (economy == null || economy.service().get(player.getUniqueId()) < cost)) {
            sendBar(player, "cannot-afford", "amount", economy == null ? String.valueOf((long) cost) : economy.service().format(cost));
            sound(player, "sounds.error");
            return;
        }
        World named = Bukkit.getWorld(arg);
        String destId = destinationId(arg);
        ConfigurationSection dest = config.getConfigurationSection("worlds." + destId);
        World world = named != null ? named : (dest == null ? null : resolveWorld(destId, dest));
        if (world == null) {
            sendBar(player, "world-missing");
            sound(player, "sounds.error");
            return;
        }
        if (dest == null) dest = destFor(world);
        sendBar(player, "searching");
        searching.add(player.getUniqueId());
        ConfigurationSection destination = dest;
        pool.request(world, destination, spot -> {
            if (!player.isOnline()) return;
            if (!searching.remove(player.getUniqueId())) return;
            if (spot == null) {
                sendBar(player, "not-found");
                sound(player, "sounds.error");
                return;
            }
            startCountdown(player, spot, cost, economy);
        });
    }

    private void startCountdown(Player player, Location dest, double cost, EconomyModule economy) {
        int seconds = Math.max(0, config.getInt("countdown-seconds", 5));
        if (seconds == 0) {
            finish(player, dest, cost, economy);
            return;
        }
        int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stop(player.getUniqueId());
                return;
            }
            if (left[0] <= 0) {
                stop(player.getUniqueId());
                finish(player, dest, cost, economy);
                return;
            }
            sendBar(player, "countdown", "seconds", String.valueOf(left[0]));
            sound(player, "sounds.countdown");
            left[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void finish(Player player, Location dest, double cost, EconomyModule economy) {
        if (cost > 0) {
            if (economy == null || !economy.service().take(player.getUniqueId(), cost)) {
                sendBar(player, "cannot-afford", "amount", economy == null ? String.valueOf((long) cost) : economy.service().format(cost));
                sound(player, "sounds.error");
                return;
            }
        }
        player.teleportAsync(dest).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!Boolean.TRUE.equals(ok) || !player.isOnline()) return;
            cooldown.put(player.getUniqueId(), System.currentTimeMillis());
            int safe = config.getInt("safe-landing-seconds", 5);
            if (safe > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, safe * 20, 0, false, false, false));
                safeUntil.put(player.getUniqueId(), System.currentTimeMillis() + safe * 1000L);
            }
            sound(player, "sounds.teleport");
            String coords = dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ();
            sendBar(player, "teleported", "coordinates", coords);
        }));
    }

    private void leaveQueue(Player player) {
        UUID uuid = player.getUniqueId();
        UUID partner = partners.remove(uuid);
        boolean queued = queue.remove(uuid);
        boolean looking = searching.remove(uuid);
        boolean teleporting = pending.containsKey(uuid);
        stop(uuid);
        if (partner != null) {
            partners.remove(partner);
            searching.remove(partner);
            queue.remove(partner);
            stop(partner);
            Player other = Bukkit.getPlayer(partner);
            if (other != null) sendBar(other, "queue.cancelled");
        }
        if (queued || looking || teleporting || partner != null) {
            sendBar(player, "queue.cancelled");
            return;
        }
        sendBar(player, "queue.not-in");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!pending.containsKey(event.getPlayer().getUniqueId())) return;
        stop(event.getPlayer().getUniqueId());
        sendBar(event.getPlayer(), "cancelled");
        sound(event.getPlayer(), "sounds.error");
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Long until = safeUntil.get(player.getUniqueId());
        if (until == null) return;
        if (until < System.currentTimeMillis()) {
            safeUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
        queue.remove(event.getPlayer().getUniqueId());
        searching.remove(event.getPlayer().getUniqueId());
        UUID partner = partners.remove(event.getPlayer().getUniqueId());
        if (partner != null) {
            partners.remove(partner);
            searching.remove(partner);
            stop(partner);
        }
        safeUntil.remove(event.getPlayer().getUniqueId());
    }

    private void toggleQueue(Player player) {
        if (queue.remove(player.getUniqueId())) {
            sendBar(player, "queue.cancelled");
            return;
        }
        queue.add(player.getUniqueId());
        sendRawBar(player, lookingText(1));
    }

    private void tickQueue() {
        queue.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        if (queue.size() >= 2) {
            java.util.Iterator<UUID> iterator = queue.iterator();
            UUID first = iterator.next();
            UUID second = iterator.next();
            queue.remove(first);
            queue.remove(second);
            Player a = Bukkit.getPlayer(first);
            Player b = Bukkit.getPlayer(second);
            if (a != null && b != null) {
                partners.put(first, second);
                partners.put(second, first);
                searching.add(first);
                searching.add(second);
                pairQueue(a, b);
            }
        }
        queueDots = queueDots % 3 + 1;
        String text = lookingText(queueDots);
        for (UUID uuid : queue) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) sendRawBar(player, text);
        }
    }

    private String lookingText(int dots) {
        String base = cfg("queue.looking", "&#22AFFB&lRTP QUEUE &8▷ &fLooking for Player");
        return base + ".".repeat(Math.max(1, Math.min(3, dots)));
    }

    private void pairQueue(Player a, Player b) {
        int seconds = Math.max(1, config.getInt("queue.countdown-seconds", 5));
        ConfigurationSection dest = config.getConfigurationSection("worlds.overworld");
        World world = dest == null ? a.getWorld() : resolveWorld("overworld", dest);
        if (world == null) world = a.getWorld();
        if (dest == null) dest = destFor(world);
        pool.request(world, dest, first -> {
            if (!partners.containsKey(a.getUniqueId()) || !partners.containsKey(b.getUniqueId())) return;
            searching.remove(a.getUniqueId());
            searching.remove(b.getUniqueId());
            if (first == null) {
                partners.remove(a.getUniqueId());
                partners.remove(b.getUniqueId());
                sendBar(a, "not-found");
                sendBar(b, "not-found");
                return;
            }
            Location second = first.clone().add(config.getDouble("queue.offset", 8), 0, 0);
            second.setYaw(first.getYaw() + 180f);
            startQueueCountdown(a, first, seconds);
            startQueueCountdown(b, second, seconds);
        });
    }

    private void startQueueCountdown(Player player, Location dest, int seconds) {
        int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stop(player.getUniqueId());
                return;
            }
            if (left[0] <= 0) {
                stop(player.getUniqueId());
                player.teleportAsync(dest);
                sound(player, "sounds.teleport");
                return;
            }
            sendRawBar(player, Text.apply(cfg("queue.found",
                    "&#22AFFB&lRTP QUEUE &8▷ &fFound Player, Teleporting in &#22AFFB&n%seconds%s"),
                    "seconds", String.valueOf(left[0])));
            sound(player, "sounds.countdown");
            left[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void stop(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }

    World resolveWorld(String id, ConfigurationSection dest) {
        String named = dest.getString("world", "");
        if (named != null && !named.isBlank()) {
            World world = Bukkit.getWorld(named);
            if (world != null) return world;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + named);
            world = Bukkit.getWorld(named);
            if (world != null) return world;
        }
        World.Environment env = environment(id);
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == env) return world;
        }
        return null;
    }

    World.Environment environment(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "nether" -> World.Environment.NETHER;
            case "end", "the_end", "theend" -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
    }

    String destinationId(String arg) {
        String lower = arg.toLowerCase(Locale.ROOT);
        if (config.isConfigurationSection("worlds." + lower)) return lower;
        if (lower.equals("the_end") || lower.equals("theend")) return "end";
        World world = Bukkit.getWorld(arg);
        if (world != null) {
            return switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "end";
                default -> "overworld";
            };
        }
        return lower;
    }

    ConfigurationSection destFor(World world) {
        String id = switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "overworld";
        };
        return config.getConfigurationSection("worlds." + id);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>();
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        if (worlds != null) options.addAll(worlds.getKeys(false));
        for (World world : Bukkit.getWorlds()) options.add(world.getName());
        return Tabs.filter(options, args[0]);
    }
}
