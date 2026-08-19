package com.sharded.core.modules.killeffects;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlayerToggles;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;
import java.util.Locale;

/** Kill particle effects — /killeffect equip ez, disable, toggle visibility for others. */
public final class KillEffectsModule extends Module implements CommandExecutor, TabCompleter {

    public KillEffectsModule(ShardedCore plugin) {
        super(plugin, "killeffects");
    }

    @Override
    protected void onEnable() {
        registerCommand("killeffect", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.killeffect.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(player, "usage");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "equip" -> {
                if (args.length < 2) {
                    send(player, "equip-usage");
                    yield true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!config.isConfigurationSection("effects." + id)) {
                    send(player, "unknown-effect", "%effect%", id);
                    yield true;
                }
                String perm = config.getString("effects." + id + ".permission", "sharded.killeffect." + id);
                if (!player.hasPermission(perm)) {
                    send(player, "no-effect-permission", "%effect%", id);
                    yield true;
                }
                PlayerToggles.setKillEffect(player, id);
                send(player, "equipped", "%effect%", id.toUpperCase(Locale.ROOT));
                yield true;
            }
            case "disable", "off", "none" -> {
                PlayerToggles.setKillEffect(player, "");
                send(player, "disabled");
                yield true;
            }
            case "toggle" -> {
                boolean show = !PlayerToggles.killEffectShowOthers(player);
                PlayerToggles.setKillEffectShowOthers(player, show);
                send(player, show ? "toggle-on" : "toggle-off");
                yield true;
            }
            default -> {
                send(player, "usage");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.killeffect.use")) return List.of();
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "equip", "disable", "toggle");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            ConfigurationSection section = config.getConfigurationSection("effects");
            if (section == null) return List.of();
            return TabCompleteHelper.filter(args[1], section.getKeys(false));
        }
        return List.of();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        String effectId = PlayerToggles.killEffect(killer);
        if (effectId.isEmpty()) return;

        ConfigurationSection effect = config.getConfigurationSection("effects." + effectId);
        if (effect == null) return;

        Location loc = victim.getLocation().clone().add(0, 1, 0);
        Particle particle = Particle.valueOf(effect.getString("particle", "DRAGON_BREATH"));
        int count = effect.getInt("count", 30);
        double spread = effect.getDouble("spread", 0.4);
        double speed = effect.getDouble("speed", 0.02);

        boolean killerShowsOthers = PlayerToggles.killEffectShowOthers(killer);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(loc.getWorld())) continue;
            if (!PlayerToggles.seeKillEffects(viewer)) continue;
            if (viewer.equals(killer)) {
                viewer.spawnParticle(particle, loc, count, spread, spread, spread, speed);
                continue;
            }
            if (!killerShowsOthers) continue;
            viewer.spawnParticle(particle, loc, count, spread, spread, spread, speed);
        }
    }
}
