package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Weighted action-bar reward spin with configurable rarity colors. */
public final class RewardSpin {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    public record RewardOption(String display, String rarityLabel, String rarityColor, double weight,
                               double percent, List<String> commands) {

        public String coloredRarity() {
            return rarityColor + rarityLabel;
        }

        public String percentText() {
            if (Math.abs(percent - Math.rint(percent)) < 0.05) {
                return String.valueOf(Math.round(percent));
            }
            return String.format(Locale.ROOT, "%.1f", percent);
        }
    }

    private RewardSpin() {
    }

    public static boolean isClaiming(UUID uuid) {
        return ACTIVE.contains(uuid);
    }

    public static List<RewardOption> loadOptions(ConfigurationSection rewardsSection) {
        List<RewardOption> list = new ArrayList<>();
        if (rewardsSection == null) return list;

        double totalWeight = 0;
        List<ConfigurationSection> sections = new ArrayList<>();
        for (String key : rewardsSection.getKeys(false)) {
            ConfigurationSection reward = rewardsSection.getConfigurationSection(key);
            if (reward == null) continue;
            sections.add(reward);
            totalWeight += reward.getDouble("weight", 1.0);
        }
        if (totalWeight <= 0) totalWeight = 1;

        for (ConfigurationSection reward : sections) {
            double weight = reward.getDouble("weight", 1.0);
            double percent = (weight / totalWeight) * 100.0;
            list.add(new RewardOption(
                    reward.getString("display", "Reward"),
                    reward.getString("rarity-label", reward.getString("rarity", "Common")),
                    reward.getString("rarity-color", "&7"),
                    weight,
                    percent,
                    reward.getStringList("commands")));
        }
        return list;
    }

    /** Starts a spin; returns false if the player is already claiming. */
    public static boolean spin(com.sharded.core.module.Module module, ShardedCore plugin, Player player,
                               List<RewardOption> options, String lastClaimKey, String winMessageKey,
                               YamlConfiguration config) {
        UUID uuid = player.getUniqueId();
        if (!ACTIVE.add(uuid)) {
            return false;
        }

        plugin.stateStore().setLong(uuid, lastClaimKey, System.currentTimeMillis());

        RewardOption winner = weightedPick(options);
        int spins = config.getInt("spin-ticks", 40);
        String format = config.getString("spin-format",
                "&dSpinning... &f%reward% %rarity_color%[%rarity% &8(%percent%%)&r%rarity_color%&8]");

        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    holder[0].cancel();
                    ACTIVE.remove(uuid);
                    return;
                }
                RewardOption shown = tick >= spins - 1
                        ? winner
                        : options.get(ThreadLocalRandom.current().nextInt(options.size()));
                player.sendActionBar(Text.c(applyFormat(format, shown)));
                tick++;
                if (tick >= spins) {
                    holder[0].cancel();
                    grant(player, winner);
                    module.send(player, winMessageKey,
                            "%reward%", winner.display(),
                            "%rarity%", winner.coloredRarity(),
                            "%percent%", winner.percentText());
                    playSound(player, config.getString("sounds.win", "ENTITY_PLAYER_LEVELUP"));
                    ACTIVE.remove(uuid);
                }
            }
        }, 0L, 2L);
        playSound(player, config.getString("sounds.spin", "UI_BUTTON_CLICK"));
        return true;
    }

    private static String applyFormat(String format, RewardOption option) {
        return format
                .replace("%reward%", option.display())
                .replace("%rarity%", option.rarityLabel())
                .replace("%rarity_color%", option.rarityColor())
                .replace("%rarity_colored%", option.coloredRarity())
                .replace("%percent%", option.percentText());
    }

    private static void grant(Player player, RewardOption reward) {
        for (String cmd : reward.commands()) {
            String parsed = cmd.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%player_name%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private static RewardOption weightedPick(List<RewardOption> options) {
        double total = options.stream().mapToDouble(RewardOption::weight).sum();
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double acc = 0;
        for (RewardOption option : options) {
            acc += option.weight();
            if (roll <= acc) return option;
        }
        return options.get(options.size() - 1);
    }

    private static void playSound(Player player, String name) {
        if (name == null || name.isBlank()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
