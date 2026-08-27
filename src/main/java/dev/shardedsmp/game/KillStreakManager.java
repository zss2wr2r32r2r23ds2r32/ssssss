package dev.shardedsmp.game;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillStreakManager {
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public int addKill(Player killer) {
        int streak = streaks.getOrDefault(killer.getUniqueId(), 0) + 1;
        streaks.put(killer.getUniqueId(), streak);
        apply(killer, streak);
        return streak;
    }

    public void reset(Player player) {
        streaks.remove(player.getUniqueId());
        removeManagedEffects(player);
    }

    public int get(Player player) {
        return streaks.getOrDefault(player.getUniqueId(), 0);
    }

    private void apply(Player player, int streak) {
        if (streak >= 3) {
            player.addPotionEffect(infinite(PotionEffectType.FIRE_RESISTANCE, 0));
        }
        if (streak >= 5) {
            player.addPotionEffect(infinite(PotionEffectType.SPEED, 1));
        }
        if (streak >= 10) {
            player.addPotionEffect(infinite(PotionEffectType.STRENGTH, 1));
        }
    }

    private void removeManagedEffects(Player player) {
        removeIfInfinite(player, PotionEffectType.FIRE_RESISTANCE);
        removeIfInfinite(player, PotionEffectType.SPEED);
        removeIfInfinite(player, PotionEffectType.STRENGTH);
    }

    private void removeIfInfinite(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect != null && effect.isInfinite()) {
            player.removePotionEffect(type);
        }
    }

    private PotionEffect infinite(PotionEffectType type, int amplifier) {
        return new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true);
    }
}
