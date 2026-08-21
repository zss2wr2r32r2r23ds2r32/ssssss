package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.HoldTracker;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private final Set<UUID> inPvp = new HashSet<>();
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
                exitPvp(player);
            }
        }
        enterTrackers.clear();
        leaveTrackers.clear();
        HandlerList.unregisterAll(this);
    }

    private void trackEnter(Player player, UUID uuid, long holdMillis) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isEnterSword(hand)) {
            enterTrackers.remove(uuid);
            return;
        }

        HoldTracker tracker = enterTrackers.computeIfAbsent(uuid, k -> new HoldTracker());
        tracker.update(player);
        showActionBar(player, tracker, holdMillis, "action-bar.enter", "&cEntering PvP in &f%seconds%s");

        if (tracker.hasHeldFor(holdMillis)) {
            enterTrackers.remove(uuid);
            enterPvp(player);
        }
    }

    private void trackLeave(Player player, UUID uuid, long holdMillis) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isLeaveSword(hand)) {
            leaveTrackers.remove(uuid);
            return;
        }

        HoldTracker tracker = leaveTrackers.computeIfAbsent(uuid, k -> new HoldTracker());
        tracker.update(player);
        showActionBar(player, tracker, holdMillis, "action-bar.leave", "&aLeaving PvP in &f%seconds%s");

        if (tracker.hasHeldFor(holdMillis)) {
            leaveTrackers.remove(uuid);
            exitPvp(player);
        }
    }

    private void showActionBar(Player player, HoldTracker tracker, long holdMillis, String path, String fallback) {
        long remainingMs = holdMillis - tracker.getHeldMillis();
        long seconds = Math.max(1, (remainingMs + 999) / 1000);
        String message = config.getString(path, fallback).replace("%seconds%", String.valueOf(seconds));
        MessageUtil.sendActionBar(player, MessageUtil.format(message, player));
    }

    private boolean isEnterSword(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = Material.matchMaterial(config.getString("enter-item.material", "DIAMOND_SWORD"));
        return ItemBuilder.matchesMaterial(item, material) &&
                ItemBuilder.matchesName(item, config.getString("enter-item.name", "&cPvP Sword"));
    }

    private boolean isLeaveSword(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = Material.matchMaterial(config.getString("leave-item.material", "WOODEN_SWORD"));
        return ItemBuilder.matchesMaterial(item, material) &&
                ItemBuilder.matchesName(item, config.getString("leave-item.name", "&aLeave PvP"));
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
        MessageUtil.sendFormatted(player, config.getString("messages.entered", "&cYou entered PvP mode!"));
    }

    private void exitPvp(Player player) {
        UUID uuid = player.getUniqueId();
        if (!inPvp.contains(uuid)) {
            return;
        }

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
        MessageUtil.sendFormatted(player, config.getString("messages.left", "&aYou left PvP mode!"));
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
                inv.setItem(slot, ItemBuilder.fromConfig(itemSection, player));
            }
        }
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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        enterTrackers.remove(uuid);
        leaveTrackers.remove(uuid);
        if (inPvp.contains(uuid)) {
            inPvp.remove(uuid);
            savedInventories.remove(uuid);
            savedArmor.remove(uuid);
            savedGameModes.remove(uuid);
        }
    }

    public boolean isInPvp(UUID uuid) {
        return inPvp.contains(uuid);
    }
}
