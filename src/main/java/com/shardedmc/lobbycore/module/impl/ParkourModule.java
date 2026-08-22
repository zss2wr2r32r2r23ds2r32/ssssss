package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public class ParkourModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private List<Material> blockMaterials;
    private List<String> endCommands;
    private final Set<UUID> inParkour = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();

    @Override
    public String getId() {
        return "parkour";
    }

    @Override
    public String getDisplayName() {
        return "Parkour";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        reloadMaterials();
        endCommands = config.getStringList("end-commands").stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        if (endCommands.isEmpty()) {
            endCommands = List.of("ajparkour leave", "ajparkour quit", "ajparkour stop", "ajparkour end");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerAjParkourListener();
    }

    private void registerAjParkourListener() {
        Plugin ajParkour = Bukkit.getPluginManager().getPlugin("ajParkour");
        if (ajParkour == null) {
            ajParkour = Bukkit.getPluginManager().getPlugin("ajparkour");
        }
        if (ajParkour == null) {
            return;
        }
        try {
            Class<? extends Event> eventClass = Class.forName("us.ajg0702.parkour.api.events.PlayerEndParkourEvent")
                    .asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> {
                try {
                    Player player = (Player) eventClass.getMethod("getPlayer").invoke(event);
                    scheduleRestore(player);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Error handling ajParkour end event: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin, false);
            plugin.getLogger().info("Hooked into ajParkour PlayerEndParkourEvent");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not hook ajParkour events: " + ex.getMessage());
        }
    }

    private void reloadMaterials() {
        blockMaterials = config.getStringList("blocks").stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (blockMaterials.isEmpty()) {
            Material material = Material.matchMaterial(config.getString("block.material", "LIME_GLAZED_TERRACOTTA"));
            if (material != null) {
                blockMaterials = List.of(material);
            }
        }
    }

    @Override
    public void disable() {
        for (UUID uuid : new HashSet<>(inParkour)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                forceRestoreLobby(player);
            } else {
                clearTracking(uuid);
            }
        }
        HandlerList.unregisterAll(this);
    }

    public boolean isParkourBlock(Material material) {
        return blockMaterials.contains(material);
    }

    public boolean isInParkour(UUID uuid) {
        return inParkour.contains(uuid);
    }

    public void startParkour(Player player) {
        if (inParkour.contains(player.getUniqueId())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        savedInventories.put(uuid, player.getInventory().getContents().clone());
        savedArmor.put(uuid, player.getInventory().getArmorContents().clone());
        savedGameModes.put(uuid, player.getGameMode());

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setAllowFlight(false);
        player.setFlying(false);
        inParkour.add(uuid);

        String command = config.getString("command", "ajparkour start").replace("%player%", player.getName());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, command));

        if (config.getBoolean("messages.start.enabled", false)) {
            MessageUtil.sendFormatted(player, config.getString("messages.start.text", "%prefix% &#9FFF00Parkour started!"));
        }
    }

    public void endParkour(Player player, boolean restoreItems) {
        if (restoreItems) {
            scheduleRestore(player);
        } else {
            UUID uuid = player.getUniqueId();
            if (inParkour.remove(uuid)) {
                clearTracking(uuid);
            }
        }
    }

    public void endParkourFromVoid(Player player) {
        scheduleRestore(player);
    }

    private void scheduleRestore(Player player) {
        UUID uuid = player.getUniqueId();
        inParkour.remove(uuid);
        clearTracking(uuid);

        long delay = config.getLong("restore-delay-ticks", 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> forceRestoreLobby(player), delay);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DoubleJumpModule doubleJump = (DoubleJumpModule) plugin.getModuleManager().getModule("double-jump");
            if (doubleJump != null) {
                doubleJump.resetPlayer(player);
            }
        }, delay + 10L);
    }

    public void forceRestoreLobby(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        if (config.getBoolean("restore-adventure-mode", true)) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        player.setFlying(false);

        DefaultItemsModule defaultItems = (DefaultItemsModule) plugin.getModuleManager().getModule("default-items");
        if (defaultItems != null) {
            defaultItems.giveItems(player);
        }

        PlayerVisibilityModule visibility = (PlayerVisibilityModule) plugin.getModuleManager().getModule("player-visibility");
        if (visibility != null) {
            visibility.updateItem(player);
        }

        DoubleJumpModule doubleJump = (DoubleJumpModule) plugin.getModuleManager().getModule("double-jump");
        if (doubleJump != null) {
            doubleJump.resetPlayer(player);
        } else if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
            player.setFlying(false);
        }
    }

    private void clearTracking(UUID uuid) {
        savedInventories.remove(uuid);
        savedArmor.remove(uuid);
        savedGameModes.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null || !blockMaterials.contains(event.getClickedBlock().getType())) {
            return;
        }

        event.setCancelled(true);
        startParkour(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!inParkour.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        String message = event.getMessage().substring(1).toLowerCase(Locale.ROOT);
        for (String endCommand : endCommands) {
            if (message.equals(endCommand) || message.startsWith(endCommand + " ")) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleRestore(event.getPlayer()), 2L);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearTracking(event.getPlayer().getUniqueId());
        inParkour.remove(event.getPlayer().getUniqueId());
    }
}
