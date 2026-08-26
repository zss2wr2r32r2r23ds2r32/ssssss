package com.shardedcore.modules.settings;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.live.LiveModule;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SettingsModule extends Module implements CommandExecutor, Listener {

    public static final String KEY_PUBLIC_CHAT = "public-chat";
    public static final String KEY_MSG = "msgtoggle";
    public static final String KEY_JOIN = "jointoggle";
    public static final String KEY_DEATH = "deathtoggle";
    public static final String KEY_MOB = "mobtoggle";
    public static final String KEY_PAY = "paytoggle";
    public static final String KEY_NIGHT_VISION = "nightvision";
    public static final String KEY_MENTIONS = "mentions";
    public static final String KEY_TPA_IN = "tpa-incoming-enabled";
    public static final String KEY_TPA_HERE = "tpa-here-enabled";
    public static final String KEY_TPA_AUTO = "tpa-auto";

    private SettingsGuiHandler guiHandler;

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    public void enable() {
        guiHandler = new SettingsGuiHandler(this);
        registerListener(this);
        registerCommand("settings", this);
        registerCommand("chattoggle", this);
        registerCommand("msgtoggle", this);
        registerCommand("jointoggle", this);
        registerCommand("deathtoggle", this);
        registerCommand("mobtoggle", this);
        registerCommand("paytoggle", this);
        registerCommand("nightvision", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    public Map<String, String> placeholders(Player player) {
        Map<String, String> map = new HashMap<>();
        map.put("status_public_chat", formatStatus(getBool(player, KEY_PUBLIC_CHAT, true)));
        map.put("status_private_messages", formatStatus(getBool(player, KEY_MSG, true)));
        map.put("status_join_messages", formatStatus(getBool(player, KEY_JOIN, true)));
        map.put("status_death_messages", formatStatus(getBool(player, KEY_DEATH, true)));
        map.put("status_mob_toggle", formatStatus(getBool(player, KEY_MOB, true)));
        map.put("status_pay", formatStatus(!getBool(player, KEY_PAY, false)));
        map.put("status_nightvision", formatStatus(getBool(player, KEY_NIGHT_VISION, false)));
        map.put("status_live", formatStatus(getBool(player, LiveModule.STATE_KEY, true)));
        map.put("status_mentions", formatStatus(getBool(player, KEY_MENTIONS, true)));
        map.put("status_tpa", formatStatus(getBool(player, KEY_TPA_IN, true)));
        map.put("status_tpa_auto", formatStatus(getBool(player, KEY_TPA_AUTO, false)));
        return map;
    }

    public String formatStatus(boolean enabled) {
        String key = enabled ? "status.enabled" : "status.disabled";
        return config.getString(key, enabled ? "&#9FFF00&lENABLED" : "&#FF2727&lDISABLED");
    }

    public boolean getBool(Player player, String key, boolean defaultValue) {
        return plugin.stateStore().getBool(player.getUniqueId(), key, defaultValue);
    }

    public boolean toggleSetting(Player player, String key, boolean defaultValue, String effect) {
        boolean next = !getBool(player, key, defaultValue);
        plugin.stateStore().setBool(player.getUniqueId(), key, next);
        applyEffect(player, effect, next);
        return next;
    }

    private void applyEffect(Player player, String effect, boolean enabled) {
        if (effect == null || effect.isBlank()) return;
        if (effect.equals("nightvision")) {
            if (enabled) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            } else {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    public void openSettings(Player player) {
        if (!player.hasPermission("shardedcore.settings.use")) {
            send(player, "no-permission");
            return;
        }
        guiHandler.open(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "settings" -> {
                openSettings(player);
                yield true;
            }
            case "chattoggle" -> {
                toggle(player, KEY_PUBLIC_CHAT, true, null, "chat-on", "chat-off");
                yield true;
            }
            case "msgtoggle" -> {
                toggle(player, KEY_MSG, true, null, "msg-on", "msg-off");
                yield true;
            }
            case "jointoggle" -> {
                toggle(player, KEY_JOIN, true, null, "join-on", "join-off");
                yield true;
            }
            case "deathtoggle" -> {
                toggle(player, KEY_DEATH, true, null, "death-on", "death-off");
                yield true;
            }
            case "mobtoggle" -> {
                toggle(player, KEY_MOB, true, null, "mob-on", "mob-off");
                yield true;
            }
            case "paytoggle" -> {
                boolean disabled = plugin.stateStore().toggle(player.getUniqueId(), KEY_PAY, false);
                send(player, disabled ? "pay-off" : "pay-on");
                yield true;
            }
            case "nightvision", "nvtoggle" -> {
                toggle(player, KEY_NIGHT_VISION, false, "nightvision", "nv-on", "nv-off");
                yield true;
            }
            default -> true;
        };
    }

    public boolean togglePay(Player player) {
        return plugin.stateStore().toggle(player.getUniqueId(), KEY_PAY, false);
    }

    private void toggle(Player player, String key, boolean defaultValue, String effect, String onKey, String offKey) {
        boolean enabled = toggleSetting(player, key, defaultValue, effect);
        send(player, enabled ? onKey : offKey);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        SettingsGuiHandler.SettingsGuiHolder holder = com.shardedcore.util.TrackedInventories.lookup(
                event.getView().getTopInventory(), SettingsGuiHandler.SettingsGuiHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, event.getSlot());
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        World world = event.getEntity().getWorld();
        if (world.getPlayers().isEmpty()) return;
        var loc = event.getLocation();
        for (Player player : world.getPlayers()) {
            if (getBool(player, KEY_MOB, true)) continue;
            if (player.getLocation().distanceSquared(loc) <= 256) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public static boolean isPublicChatEnabled(UUID uuid, ShardedCore plugin) {
        return plugin.stateStore().getBool(uuid, KEY_PUBLIC_CHAT, true);
    }

    public static boolean isMsgEnabled(UUID uuid, ShardedCore plugin) {
        return plugin.stateStore().getBool(uuid, KEY_MSG, true);
    }

    public static boolean isDeathMessagesEnabled(UUID uuid, ShardedCore plugin) {
        return plugin.stateStore().getBool(uuid, KEY_DEATH, true);
    }

    public static boolean isJoinMessagesEnabled(UUID uuid, ShardedCore plugin) {
        return plugin.stateStore().getBool(uuid, KEY_JOIN, true);
    }
}
