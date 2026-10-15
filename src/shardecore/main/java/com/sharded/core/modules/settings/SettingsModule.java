package com.sharded.core.modules.settings;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.live.LiveModule;
import com.sharded.core.modules.nightvision.NightVisionModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.util.PlayerToggles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class SettingsModule extends SetupFeatureModule implements CommandExecutor, Listener {

    private SettingsData settingsData;
    private final Map<Integer, String> page1Slots = new HashMap<>();

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        ensureSettingsLayout();
        settingsData = new SettingsData(plugin);
        settingsData.load();
        loadSlotMaps();
        this.registerCommand("settings", this);
        this.registerCommand("deathtoggle", this);
        this.registerCommand("jointoggle", this);
        this.registerCommand("eventsoundstoggle", this);
    }

    @Override
    protected void onDisable() {
        if (settingsData != null) {
            settingsData.save();
        }
    }

    @Override
    public void reload() {
        super.reload();
        loadFiles();
        ensureSettingsLayout();
        loadSlotMaps();
    }

    public Map<String, String> placeholders(Player player) {
        Map<String, String> map = new HashMap<>();
        map.put("status_scoreboard", statusText(isEnabled(player.getUniqueId(), "scoreboard")));
        map.put("status_death_messages", statusText(isEnabled(player.getUniqueId(), "death-msgs")));
        map.put("status_join_messages", statusText(isEnabled(player.getUniqueId(), "join-leave")));
        map.put("status_public_chat", statusText(isEnabled(player.getUniqueId(), "public-chat")));
        map.put("status_private_messages", statusText(isEnabled(player.getUniqueId(), "private-msg")));
        map.put("status_event_sounds", statusText(isEnabled(player.getUniqueId(), "rtpqueue")));
        return map;
    }

    private String statusText(boolean enabled) {
        return enabled
                ? config.getString("on-color", "&#9FFF00") + config.getString("on-text", "&lON")
                : config.getString("off-color", "&#FF2121") + config.getString("off-text", "&lOFF");
    }

    public boolean isEnabled(UUID uuid, String key) {
        boolean def = config.getBoolean("defaults." + key, true);
        return settingsData == null || settingsData.get(uuid, key, def);
    }

    private void ensureSettingsLayout() {
        org.bukkit.configuration.file.FileConfiguration bundled = loadBundledConfig();
        int jar = bundled != null ? bundled.getInt("config-version", 11) : 11;
        if (bundled == null || config.getInt("config-version", 0) >= jar) {
            return;
        }
        if (bundled.getConfigurationSection("page1.items") == null) {
            return;
        }
        for (String key : new java.util.ArrayList<>(this.config.getKeys(false))) {
            this.config.set(key, null);
        }
        for (String key : bundled.getKeys(false)) {
            this.config.set(key, bundled.get(key));
        }
        saveConfig();
    }

    private void loadSlotMaps() {
        page1Slots.clear();
        ConfigurationSection section = config.getConfigurationSection("page1.items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            page1Slots.put(config.getInt("page1.items." + key + ".slot"), key);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String name = command.getName().toLowerCase();
        if (name.equals("settings") || name.equals("setting")) {
            open(player);
            return true;
        }
        switch (name) {
            case "deathtoggle", "deathmessages", "dtoggle" -> flip(player, "death-msgs");
            case "jointoggle", "joinmessages", "joinleave", "jtoggle" -> flip(player, "join-leave");
            case "eventsoundstoggle", "eventsounds" -> flip(player, "rtpqueue");
            default -> open(player);
        }
        return true;
    }

    private void flip(Player player, String key) {
        boolean def = config.getBoolean("defaults." + key, true);
        settingsData.toggle(player.getUniqueId(), key, def);
        applySideEffects(player, key, isEnabled(player.getUniqueId(), key));
        open(player);
    }

    private void open(Player player) {
        Inventory inventory = GuiHelper.create("settings-1",
                config.getString("page1.title", "&8Settings"),
                config.getInt("page1.rows", 5));
        switch (config.getString("filler-style", "bordered-inner").toLowerCase()) {
            case "glass", "gray" -> GuiHelper.fillGlass(inventory);
            case "dark" -> GuiHelper.fillDarkGlass(inventory);
            case "border" -> GuiHelper.fillBorder(inventory);
            default -> GuiHelper.fillBorderedInner(inventory);
        }
        ConfigurationSection items = config.getConfigurationSection("page1.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                String itemPath = "page1.items." + key + ".";
                Material material = MaterialUtil.resolveWithFallbacks(
                        config.getString(itemPath + "material"),
                        config.getString(itemPath + "fallback"),
                        config.getString(itemPath + "fallback-2"));
                if (material == null) {
                    material = Material.PAPER;
                }
                boolean enabled = isEnabled(player.getUniqueId(), key);
                String status = enabled
                        ? config.getString("on-color", "&#9FFF00") + config.getString("on-text", "&lON")
                        : config.getString("off-color", "&#FF2121") + config.getString("off-text", "&lOFF");
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList(itemPath + "lore")) {
                    lore.add(line.replace("%status%", status)
                            .replace("%on_color%", config.getString("on-color", "&#9FFF00"))
                            .replace("%off_color%", config.getString("off-color", "&#FF2121")));
                }
                inventory.setItem(config.getInt(itemPath + "slot"), ItemBuilder.of(material)
                        .name(config.getString(itemPath + "name", key))
                        .lore(lore)
                        .hideExtras()
                        .build());
            }
        }
        int closeSlot = config.getInt("page1.close-slot", 40);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            inventory.setItem(closeSlot, GuiHelper.closeButton());
        }
        player.openInventory(inventory);
        SoundUtil.play(player, config.getString("sound-open", "block.note_block.pling"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        String id = holder.getId();
        if (id == null || !id.startsWith("settings-")) {
            return;
        }
        event.setCancelled(true);
        int closeSlot = config.getInt("page1.close-slot", 40);
        if (event.getRawSlot() == closeSlot) {
            player.closeInventory();
            return;
        }
        String key = page1Slots.get(event.getRawSlot());
        if (key == null) {
            return;
        }
        boolean def = config.getBoolean("defaults." + key, true);
        settingsData.toggle(player.getUniqueId(), key, def);
        boolean enabled = isEnabled(player.getUniqueId(), key);
        applySideEffects(player, key, enabled);
        String settingRaw = config.getString("page1.items." + key + ".name", key);
        String settingName = settingRaw
                .replaceAll("&#[0-9A-Fa-f]{6}", "")
                .replaceAll("&x(&[0-9A-Fa-f]){6}", "")
                .replaceAll("&[0-9a-fklmnorA-FKLMNOR]", "")
                .trim();
        this.send(player, enabled ? "toggled-enabled" : "toggled-disabled", "%setting%", settingName);
        SoundUtil.play(player, config.getString("sound-toggle", "ui.button.click"));
        open(player);
    }

    private void applySideEffects(Player player, String key, boolean enabled) {
        switch (key) {
            case "public-chat" -> {
                ChatToggleModule chat = plugin.modules().get(ChatToggleModule.class);
                if (chat != null) {
                    plugin.stateStore().setBool(player.getUniqueId(), ChatToggleModule.STATE_KEY, enabled);
                }
            }
            case "private-msg" -> plugin.stateStore().setBool(player.getUniqueId(), PrivateMessagesModule.STATE_KEY, enabled);
            case "live" -> {
                LiveModule live = plugin.modules().get(LiveModule.class);
                if (live != null) {
                    live.setLiveEnabled(player, enabled);
                }
            }
            case "scoreboard" -> {
                PlayerToggles.setScoreboard(player, enabled);
                final String tabCmd = enabled ? "tab scoreboard on" : "tab scoreboard off";
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(tabCmd));
            }
            case "night-vision" -> {
                NightVisionModule nv = plugin.modules().get(NightVisionModule.class);
                if (nv != null) {
                    nv.setNightVision(player, enabled);
                }
            }
            case "death-msgs" -> PlayerToggles.setDeathMessages(player, enabled);
            case "join-leave" -> PlayerToggles.setJoinMessages(player, enabled);
            case "rtpqueue" -> PlayerToggles.setEventSounds(player, enabled);
            default -> {
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled(event.getPlayer().getUniqueId(), "scoreboard")) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isEnabled(player.getUniqueId(), "scoreboard")) {
                    player.performCommand("tab scoreboard off");
                }
            }, 40L);
        }
    }
}
