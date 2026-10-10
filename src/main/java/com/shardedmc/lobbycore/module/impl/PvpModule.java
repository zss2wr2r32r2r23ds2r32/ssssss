package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.HoldTracker;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PvpModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private BukkitTask tickTask;
    private final Map<UUID, HoldTracker> enterTrackers = new HashMap<>();
    private final Map<UUID, HoldTracker> leaveTrackers = new HashMap<>();
    private final Map<UUID, Long> lastEnterSecond = new HashMap<>();
    private final Map<UUID, Long> lastLeaveSecond = new HashMap<>();
    private final Set<UUID> inPvp = new HashSet<>();
    private final Set<UUID> dyingPlayers = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();

    @Override
    public String getId() {
        return "pvp";
    }

    @Override
    public String getDisplayName() {
        return "PvP Arena";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        long holdMillis = config.getLong("hold-seconds", 5) * 1000L;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (inPvp.contains(uuid)) {
                    trackLeave(player, uuid, holdMillis);
                } else {
                    trackEnter(player, uuid, holdMillis);
                }
            }
        }, 0L, 2L);
    }

    @Override
    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (UUID uuid : new HashSet<>(inPvp)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restoreLobbyInventory(player, uuid, false);
            }
        }
        enterTrackers.clear();
        leaveTrackers.clear();
        lastEnterSecond.clear();
        lastLeaveSecond.clear();
        HandlerList.unregisterAll(this);
    }

    private void trackEnter(Player player, UUID uuid, long holdMillis) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isEnterSword(hand)) {
            enterTrackers.remove(uuid);
            lastEnterSecond.remove(uuid);
            return;
        }

        HoldTracker tracker = enterTrackers.computeIfAbsent(uuid, k -> new HoldTracker());
        tracker.update(player);
        showActionBar(player, tracker, holdMillis, "action-bar.enter", "&#FF0000&lPVP &8▷ &fEntering PVP In &#FF0000%seconds%s", lastEnterSecond);

        if (tracker.hasHeldFor(holdMillis)) {
            enterTrackers.remove(uuid);
            lastEnterSecond.remove(uuid);
            enterPvp(player);
        }
    }

    private void trackLeave(Player player, UUID uuid, long holdMillis) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isLeaveSword(hand)) {
            leaveTrackers.remove(uuid);
            lastLeaveSecond.remove(uuid);
            return;
        }

        HoldTracker tracker = leaveTrackers.computeIfAbsent(uuid, k -> new HoldTracker());
        tracker.update(player);
        showActionBar(player, tracker, holdMillis, "action-bar.leave", "&#FF0000&lPVP &8▷ &fLeaving PVP In &#FF0000%seconds%s", lastLeaveSecond);

        if (tracker.hasHeldFor(holdMillis)) {
            leaveTrackers.remove(uuid);
            lastLeaveSecond.remove(uuid);
            exitPvp(player);
        }
    }

    private void showActionBar(Player player, HoldTracker tracker, long holdMillis, String path, String fallback, Map<UUID, Long> lastSecondMap) {
        long remainingMs = holdMillis - tracker.getHeldMillis();
        long seconds = Math.max(1, (remainingMs + 999) / 1000);

        UUID uuid = player.getUniqueId();
        Long lastSecond = lastSecondMap.get(uuid);
        if (lastSecond == null || lastSecond != seconds) {
            lastSecondMap.put(uuid, seconds);
            playCountdownSound(player, seconds);
        }

        String message = config.getString(path, fallback).replace("%seconds%", String.valueOf(seconds));
        MessageUtil.sendActionBar(player, MessageUtil.format(message, player));
    }

    private void playCountdownSound(Player player, long secondsRemaining) {
        if (!config.getBoolean("countdown-sounds.enabled", true)) {
            return;
        }

        String soundName = config.getString("countdown-sounds.sound", "BLOCK_NOTE_BLOCK_PLING");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        float volume = (float) config.getDouble("countdown-sounds.volume", 1.0);
        float basePitch = (float) config.getDouble("countdown-sounds.pitch", 1.0);
        float pitchStep = (float) config.getDouble("countdown-sounds.pitch-step", 0.15);
        long holdSeconds = config.getLong("hold-seconds", 5);
        float pitch = basePitch + (float) (holdSeconds - secondsRemaining) * pitchStep;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private boolean isEnterSword(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = Material.matchMaterial(config.getString("enter-item.material", "DIAMOND_SWORD"));
        if (!ItemBuilder.matchesMaterial(item, material)) {
            return false;
        }
        if (ItemBuilder.matchesName(item, config.getString("enter-item.name", "&cPvP Sword"))) {
            return true;
        }
        DefaultItemsModule defaultItems = (DefaultItemsModule) plugin.getModuleManager().getModule("default-items");
        if (defaultItems != null) {
            ConfigurationSection section = defaultItems.getItemSection("pvp-sword");
            if (section != null && ItemBuilder.matchesName(item, section.getString("name"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isLeaveSword(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material leaveMaterial = Material.matchMaterial(config.getString("leave-item.material", "NETHERITE_SWORD"));
        if (ItemBuilder.matchesMaterial(item, leaveMaterial) &&
                ItemBuilder.matchesName(item, config.getString("leave-item.name", "&aLeave PvP"))) {
            return true;
        }

        ConfigurationSection kitLeave = config.getConfigurationSection("kit.items.leave-sword");
        if (kitLeave != null) {
            Material kitMaterial = Material.matchMaterial(kitLeave.getString("material", "NETHERITE_SWORD"));
            if (ItemBuilder.matchesMaterial(item, kitMaterial) &&
                    ItemBuilder.matchesName(item, kitLeave.getString("name"))) {
                return true;
            }
        }
        return false;
    }

    private void enterPvp(Player player) {
        UUID uuid = player.getUniqueId();
        if (inPvp.contains(uuid)) {
            return;
        }

        savedInventories.put(uuid, player.getInventory().getContents().clone());
        savedArmor.put(uuid, player.getInventory().getArmorContents().clone());
        savedGameModes.put(uuid, player.getGameMode());

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        giveKit(player);
        inPvp.add(uuid);
        MessageUtil.sendFormatted(player, config.getString("messages.entered", "%prefix% &#9FFF00You have entered PvP mode, you may fight!"));
    }

    private void exitPvp(Player player) {
        UUID uuid = player.getUniqueId();
        if (!inPvp.contains(uuid)) {
            return;
        }
        restoreLobbyInventory(player, uuid, true);
    }

    private void restoreLobbyInventory(Player player, UUID uuid, boolean sendMessage) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        ItemStack[] inv = savedInventories.remove(uuid);
        ItemStack[] armor = savedArmor.remove(uuid);
        GameMode mode = savedGameModes.remove(uuid);
        inPvp.remove(uuid);

        if (inv != null) {
            player.getInventory().setContents(inv);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        if (mode != null) {
            player.setGameMode(mode);
        }

        DefaultItemsModule defaultItems = (DefaultItemsModule) plugin.getModuleManager().getModule("default-items");
        if (defaultItems != null) {
            defaultItems.giveItems(player);
        }
        PlayerVisibilityModule visibility = (PlayerVisibilityModule) plugin.getModuleManager().getModule("player-visibility");
        if (visibility != null) {
            visibility.updateItem(player);
        }

        if (sendMessage) {
            MessageUtil.sendFormatted(player, config.getString("messages.left", "%prefix% &#9FFF00You have left PvP mode, PvP is now disabled."));
        }
    }

    private void giveKit(Player player) {
        ConfigurationSection kit = config.getConfigurationSection("kit");
        if (kit == null) {
            return;
        }

        PlayerInventory inv = player.getInventory();

        if (kit.isConfigurationSection("armor")) {
            ConfigurationSection armor = kit.getConfigurationSection("armor");
            inv.setHelmet(buildArmor(armor, "helmet", Material.DIAMOND_HELMET));
            inv.setChestplate(buildArmor(armor, "chestplate", Material.DIAMOND_CHESTPLATE));
            inv.setLeggings(buildArmor(armor, "leggings", Material.DIAMOND_LEGGINGS));
            inv.setBoots(buildArmor(armor, "boots", Material.DIAMOND_BOOTS));
        }

        if (kit.isConfigurationSection("items")) {
            for (String key : kit.getConfigurationSection("items").getKeys(false)) {
                ConfigurationSection itemSection = kit.getConfigurationSection("items." + key);
                int slot = itemSection.getInt("slot");
                boolean plain = itemSection.getBoolean("plain", !"leave-sword".equals(key));
                inv.setItem(slot, ItemBuilder.fromConfig(itemSection, player, plain));
            }
        }

        int arrowSlot = config.getInt("kit.arrow-slot", 9);
        int arrowAmount = config.getInt("kit.arrow-amount", 16);
        inv.setItem(arrowSlot, new ItemStack(Material.ARROW, arrowAmount));
    }

    private ItemStack buildArmor(ConfigurationSection armor, String piece, Material fallback) {
        if (armor == null || !armor.contains(piece)) {
            return new ItemStack(fallback);
        }
        ConfigurationSection section = armor.getConfigurationSection(piece);
        Material material = Material.matchMaterial(section.getString("material", fallback.name()));
        return ItemBuilder.of(material != null ? material : fallback).build();
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        enterTrackers.remove(uuid);
        leaveTrackers.remove(uuid);
        lastEnterSecond.remove(uuid);
        lastLeaveSecond.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        enterTrackers.remove(uuid);
        leaveTrackers.remove(uuid);
        lastEnterSecond.remove(uuid);
        lastLeaveSecond.remove(uuid);
        dyingPlayers.remove(uuid);
        if (inPvp.contains(uuid)) {
            restoreLobbyInventory(player, uuid, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        if (!inPvp.contains(uuid)) {
            return;
        }

        dyingPlayers.add(uuid);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        String deathMessage = config.getString("messages.death", "&#FF256E🗡 &#FF256E%player% &fdied")
                .replace("%player%", player.getName());
        event.setDeathMessage(null);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                MessageUtil.sendFormatted(online, deathMessage);
            }
        });

        // Leave PVP immediately so they cannot be hit while dead / on respawn
        inPvp.remove(uuid);
        enterTrackers.remove(uuid);
        leaveTrackers.remove(uuid);
        lastEnterSecond.remove(uuid);
        lastLeaveSecond.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageWhileDying(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID uuid = victim.getUniqueId();
        if (dyingPlayers.contains(uuid) || victim.isDead() || victim.getHealth() <= 0) {
            event.setCancelled(true);
            return;
        }
        if (!inPvp.contains(uuid)) {
            // Outside PVP — world protection handles this, but block if dying/left mid-fight
            Player attacker = null;
            if (event.getDamager() instanceof Player p) {
                attacker = p;
            } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
            if (attacker != null && !inPvp.contains(attacker.getUniqueId())) {
                return;
            }
            if (attacker != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        dyingPlayers.remove(uuid);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Ensure lobby inventory after death (not PVP kit)
            if (!inPvp.contains(uuid)) {
                if (savedInventories.containsKey(uuid)) {
                    restoreLobbyInventory(player, uuid, false);
                } else {
                    DefaultItemsModule defaultItems = (DefaultItemsModule) plugin.getModuleManager().getModule("default-items");
                    if (defaultItems != null) {
                        defaultItems.giveItems(player);
                    }
                }
                player.teleport(plugin.getSpawnManager().getSpawn());
                player.setHealth(20.0);
                player.setFoodLevel(20);
            }
        });
    }

    public boolean isInPvp(UUID uuid) {
        return inPvp.contains(uuid) && !dyingPlayers.contains(uuid);
    }

    public boolean isDying(UUID uuid) {
        return dyingPlayers.contains(uuid);
    }
}
