package com.sharded.core.modules.nightvision;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** /nightvision (/nv) - toggles infinite night vision. Persists across relogs. */
public final class NightVisionModule extends Module implements CommandExecutor {

    public static final String STATE_KEY = "nightvision-enabled";

    public NightVisionModule(ShardedCore plugin) {
        super(plugin, "nightvision");
    }

    @Override
    protected void onEnable() {
        registerCommand("nightvision", this);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isNightVisionEnabled(player)) apply(player, true);
        }
    }

    @Override
    protected void onDisable() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    public boolean isNightVisionEnabled(Player player) {
        return plugin.stateStore().getBool(player.getUniqueId(), STATE_KEY, false);
    }

    public void setNightVision(Player player, boolean enabled) {
        plugin.stateStore().setBool(player.getUniqueId(), STATE_KEY, enabled);
        apply(player, enabled);
        send(player, enabled ? "nv-enabled" : "nv-disabled");
    }

    private void apply(Player player, boolean enabled) {
        if (enabled) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    PotionEffect.INFINITE_DURATION, 0, true, false, config.getBoolean("show-icon", true)));
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.nightvision.use")) {
            send(player, "no-permission");
            return true;
        }
        setNightVision(player, !isNightVisionEnabled(player));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isNightVisionEnabled(event.getPlayer())) {
            apply(event.getPlayer(), true);
        }
    }
}
