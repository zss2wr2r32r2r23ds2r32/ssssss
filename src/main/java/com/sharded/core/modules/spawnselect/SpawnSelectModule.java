package com.sharded.core.modules.spawnselect;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.portalrtp.PortalRtpModule;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.LocationUtil;
import com.sharded.core.util.SafeLocationFinder;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Spawn selector — main spawn vs vanilla RTP, linked to portal RTP radius on death. */
public final class SpawnSelectModule extends Module implements CommandExecutor, TabCompleter {

    private static final String STATE_KEY = "spawn-selection";

    private final Set<UUID> awaitingSelection = ConcurrentHashMap.newKeySet();

    public SpawnSelectModule(ShardedCore plugin) {
        super(plugin, "spawnselect");
    }

    @Override
    protected void onEnable() {
        File guiFile = new File(moduleFolder(), "gui.yml");
        ConfigSync.sync(plugin, guiFile, "modules/spawnselect/gui.yml");
        plugin.gui().loadMenu(guiFile, "spawnselect");

        plugin.gui().registerAction("spawn_select_main", p -> select(p, "main"));
        plugin.gui().registerAction("spawn_select_vanilla", p -> select(p, "vanilla"));

        registerCommand("spawn", this);
        registerCommand("spawnselect", this);
        registerCommand("spawnselector", this);
        registerCommand("setspawn", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("setspawn")) return handleSetSpawn(sender, args);
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (cmd.equals("spawn") || cmd.equals("spawnselect") || cmd.equals("spawnselector")) {
            return handleSpawn(player, args);
        }
        return true;
    }

    private boolean handleSpawn(Player player, String[] args) {
        if (args.length == 0) {
            openSelector(player);
            return true;
        }
        String choice = args[0].toLowerCase();
        if (choice.equals("main") || choice.equals("vanilla")) {
            select(player, choice);
            return true;
        }
        send(player, "usage");
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sharded.spawn.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "setspawn-usage");
            return true;
        }
        if (args[0].equalsIgnoreCase("main")) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            LocationUtil.write(config.createSection("main-spawn"), player.getLocation());
            saveConfig();
            send(sender, "main-set");
            return true;
        }
        if (args[0].equalsIgnoreCase("vanilla")) {
            if (args.length < 2) {
                send(sender, "vanilla-usage");
                return true;
            }
            World world = Bukkit.getWorld(args[1]);
            if (world == null) {
                send(sender, "world-not-found", "%world%", args[1]);
                return true;
            }
            config.set("vanilla-world", world.getName());
            saveConfig();
            send(sender, "vanilla-set", "%world%", world.getName());
            return true;
        }
        send(sender, "setspawn-usage");
        return true;
    }

    private void openSelector(Player player) {
        plugin.gui().open(player, "spawnselect");
    }

    private void openForcedSelector(Player player) {
        awaitingSelection.add(player.getUniqueId());
        openSelector(player);
    }

    private void select(Player player, String choice) {
        if (choice.equals("main")) {
            if (mainSpawn() == null) {
                send(player, "main-not-set");
                return;
            }
            setSelection(player, "main");
            awaitingSelection.remove(player.getUniqueId());
            send(player, "selected-main");
            return;
        }
        if (vanillaWorld() == null) {
            send(player, "vanilla-not-set");
            return;
        }
        setSelection(player, "vanilla");
        awaitingSelection.remove(player.getUniqueId());
        send(player, "selected-vanilla");
    }

    private void setSelection(Player player, String choice) {
        plugin.stateStore().setString(player.getUniqueId(), STATE_KEY, choice);
    }

    private String getSelection(Player player) {
        return plugin.stateStore().getString(player.getUniqueId(), STATE_KEY, "");
    }

    private Location mainSpawn() {
        return LocationUtil.read(config.getConfigurationSection("main-spawn"));
    }

    private String vanillaWorld() {
        return config.getString("vanilla-world", "");
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[spawnselect] Could not save config: " + e.getMessage());
        }
    }

    private boolean hasSelection(Player player) {
        String selection = getSelection(player);
        return selection != null && !selection.isBlank();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Location spawn = player.getWorld().getSpawnLocation();
                player.teleport(spawn);
            });
        }
        if (!config.getBoolean("prompt-on-join", true)) return;
        if (player.hasPlayedBefore()) return;
        if (hasSelection(player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || hasSelection(player)) return;
            openForcedSelector(player);
            send(player, "prompt-select");
        }, config.getLong("prompt-delay-ticks", 40L));
    }

    @EventHandler
    public void onSelectorClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!awaitingSelection.contains(player.getUniqueId())) return;
        if (hasSelection(player)) {
            awaitingSelection.remove(player.getUniqueId());
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                awaitingSelection.remove(player.getUniqueId());
                return;
            }
            if (hasSelection(player)) {
                awaitingSelection.remove(player.getUniqueId());
                return;
            }
            openSelector(player);
        }, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String selection = getSelection(player);

        if (selection == null || selection.isBlank()) {
            event.setRespawnLocation(player.getWorld().getSpawnLocation());
            return;
        }

        if (selection.equals("main")) {
            Location main = mainSpawn();
            if (main != null) event.setRespawnLocation(main);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> teleportRandom(player, selection.equals("vanilla")), 1L);
    }

    private void teleportRandom(Player player, boolean vanillaWorldOnly) {
        World world = resolveRtpWorld(vanillaWorldOnly);
        if (world == null) {
            if (vanillaWorldOnly) send(player, "vanilla-not-set");
            return;
        }
        Location location = findRtpLocation(world);
        if (location == null) {
            send(player, "no-safe-location");
            return;
        }
        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
                if (!vanillaWorldOnly) send(player, "random-respawn");
            }
        });
    }

    private World resolveRtpWorld(boolean vanillaWorldOnly) {
        if (vanillaWorldOnly) {
            String name = vanillaWorld();
            return name == null || name.isBlank() ? null : Bukkit.getWorld(name);
        }
        PortalRtpModule rtp = plugin.modules().get(PortalRtpModule.class);
        String target = rtp != null && rtp.isEnabled()
                ? rtp.targetWorldName()
                : config.getString("fallback-world", "world");
        return Bukkit.getWorld(target);
    }

    private Location findRtpLocation(World world) {
        PortalRtpModule rtp = plugin.modules().get(PortalRtpModule.class);
        if (rtp != null && rtp.isEnabled()) {
            Location loc = rtp.findSafeLocation(world);
            if (loc != null) return loc;
        }
        ConfigurationSection rtpSettings = config.getConfigurationSection("rtp-fallback");
        if (rtpSettings != null) return SafeLocationFinder.find(world, rtpSettings);
        return SafeLocationFinder.find(world, 0, 0, 100, 500, 25);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("setspawn")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "main", "vanilla");
        if (args.length == 2 && args[0].equalsIgnoreCase("vanilla")) {
            return TabCompleteHelper.filter(args[1], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        return List.of();
    }
}
