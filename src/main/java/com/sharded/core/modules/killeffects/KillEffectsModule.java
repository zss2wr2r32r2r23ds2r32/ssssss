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
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Kill particle effects — /killeffect equip ez, disable, toggle visibility for others. */
public final class KillEffectsModule extends Module implements CommandExecutor, TabCompleter {

    private final Map<UUID, UUID> lastPlayerDamager = new ConcurrentHashMap<>();

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && !attacker.equals(victim)) {
            lastPlayerDamager.put(victim.getUniqueId(), attacker.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = resolveKiller(victim);
        if (killer == null || killer.equals(victim)) return;

        String effectId = PlayerToggles.killEffect(killer);
        if (effectId.isEmpty()) return;

        ConfigurationSection effect = config.getConfigurationSection("effects." + effectId);
        if (effect == null) return;

        Location loc = victim.getLocation().clone().add(0, 1.2, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> playEffect(killer, loc, effect), 1L);
    }

    private Player resolveKiller(Player victim) {
        Player killer = victim.getKiller();
        if (killer != null) {
            lastPlayerDamager.remove(victim.getUniqueId());
            return killer;
        }
        UUID damagerId = lastPlayerDamager.remove(victim.getUniqueId());
        return damagerId == null ? null : Bukkit.getPlayer(damagerId);
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private void playEffect(Player killer, Location loc, ConfigurationSection effect) {
        if (loc.getWorld() == null) return;
        Particle particle = Particle.valueOf(effect.getString("particle", "DRAGON_BREATH"));
        int count = effect.getInt("count", 45);
        double spread = effect.getDouble("spread", 0.45);
        double speed = effect.getDouble("speed", 0.02);
        boolean killerShowsOthers = PlayerToggles.killEffectShowOthers(killer);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(loc.getWorld())) continue;
            if (!PlayerToggles.seeKillEffects(viewer)) continue;
            if (viewer.equals(killer) || killerShowsOthers) {
                viewer.spawnParticle(particle, loc, count, spread, spread, spread, speed);
            }
        }
    }
}
