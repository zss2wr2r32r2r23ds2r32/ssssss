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
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.EventPriority;
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
    private final Set<UUID> duelPrompt = ConcurrentHashMap.newKeySet();
    private RtpSafeSpotPool pool;

    public RtpModule(ShardedCore plugin) {
        super(plugin, "rtp");
    }

    @Override
    public void enable() {
        pool = new RtpSafeSpotPool(this);
        pool.start();
        registerCommand("rtp", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        if (pool != null) pool.shutdown();
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        cooldown.clear();
        safeUntil.clear();
        duelPrompt.clear();
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
            send(sender, "players-only");
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
        ConfigurationSection duel = config.getConfigurationSection("menu.duel");
        if (duel != null) {
            menu.set(duel.getInt("slot", 15), Items.fromSection(duel, player), event -> {
                event.setCancelled(true);
                player.closeInventory();
                startDuel(player);
            });
        }
        plugin.menus().open(player, menu);
        sound(player, "sounds.menu-open");
    }

    private ItemStack worldItem(ConfigurationSection dest, World world) {
        Material material = Sounds.material(dest.getString("material", "STONE"), Material.STONE);
        int players = 0;
        String border = "0";
        if (world != null) {
            players = world.getPlayers().size();
            border = String.valueOf((int) (world.getWorldBorder().getSize() / 2));
        }
        String radius = String.valueOf(dest.getInt("radius", 0));
        String name = Text.apply(dest.getString("name", ""), "players", String.valueOf(players), "radius", radius, "border", border);
        List<String> lore = new ArrayList<>();
        for (String line : dest.getStringList("lore")) {
            lore.add(Text.apply(line, "players", String.valueOf(players), "radius", radius, "border", border));
        }
        return Items.named(material, name, lore);
    }

    void begin(Player player, String arg) {
        if (pending.containsKey(player.getUniqueId())) {
            send(player, "already-teleporting");
            sound(player, "sounds.error");
            return;
        }
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (combat != null && combat.tagged(player)) {
            send(player, "restricted");
            sound(player, "sounds.error");
            return;
        }
        long wait = config.getLong("cooldown-seconds", 10) * 1000L;
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait) {
            long left = (wait - (System.currentTimeMillis() - last) + 999) / 1000L;
            send(player, "cooldown", "time", String.valueOf(Math.max(1, left)));
            sound(player, "sounds.error");
            return;
        }
        double cost = Amounts.parse(String.valueOf(config.get("cost", 0)));
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (cost > 0 && (economy == null || economy.service().get(player.getUniqueId()) < cost)) {
            send(player, "cannot-afford", "amount", economy == null ? String.valueOf((long) cost) : economy.service().format(cost));
            sound(player, "sounds.error");
            return;
        }
        World named = Bukkit.getWorld(arg);
        String destId = destinationId(arg);
        ConfigurationSection dest = config.getConfigurationSection("worlds." + destId);
        World world = named != null ? named : (dest == null ? null : resolveWorld(destId, dest));
        if (world == null) {
            send(player, "world-missing");
            sound(player, "sounds.error");
            return;
        }
        if (dest == null) dest = destFor(world);
        send(player, "searching");
        Location spot = pool.poll(world);
        if (spot == null) spot = pool.searchNow(world, dest);
        if (spot == null) {
            send(player, "not-found");
            sound(player, "sounds.error");
            return;
        }
        startCountdown(player, spot, cost, economy);
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
            send(player, "countdown", "seconds", String.valueOf(left[0]));
            sound(player, "sounds.countdown");
            left[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void finish(Player player, Location dest, double cost, EconomyModule economy) {
        if (cost > 0) {
            if (economy == null || !economy.service().take(player.getUniqueId(), cost)) {
                send(player, "cannot-afford", "amount", economy == null ? String.valueOf((long) cost) : economy.service().format(cost));
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
            send(player, "teleported", "coordinates", coords);
        }));
    }

    private void startDuel(Player player) {
        if (commandExists("1v1")) {
            player.performCommand("1v1");
            return;
        }
        duelPrompt.add(player.getUniqueId());
        send(player, "duel-prompt");
    }

    private boolean commandExists(String name) {
        return Bukkit.getCommandMap().getCommand(name) != null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!duelPrompt.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (typed.equalsIgnoreCase("cancel")) {
                send(player, "duel-cancelled");
                return;
            }
            Player target = Bukkit.getPlayerExact(typed);
            if (target == null) {
                send(player, "duel-unknown", "player", typed);
                return;
            }
            if (commandExists("1v1")) {
                player.performCommand("1v1 " + target.getName());
            } else if (commandExists("duel")) {
                player.performCommand("duel " + target.getName());
            } else {
                send(player, "duel-missing");
            }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!pending.containsKey(event.getPlayer().getUniqueId())) return;
        stop(event.getPlayer().getUniqueId());
        send(event.getPlayer(), "cancelled");
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
        duelPrompt.remove(event.getPlayer().getUniqueId());
        safeUntil.remove(event.getPlayer().getUniqueId());
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
