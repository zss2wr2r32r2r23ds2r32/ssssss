package com.shardedcore.modules.sell;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.*;

final class SellPrices {
    record Tier(String id, String permission, double multiplier) {}
    private final Map<Material, Long> prices = new EnumMap<>(Material.class);
    private final List<Tier> tiers = new ArrayList<>();

    SellPrices(File pricesFile, File multiplierFile) {
        YamlConfiguration pricesYaml = YamlConfiguration.loadConfiguration(pricesFile);
        ConfigurationSection section = pricesYaml.getConfigurationSection("prices");
        if (section != null) for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (mat != null) prices.put(mat, pricesYaml.getLong("prices." + key));
        }
        YamlConfiguration multYaml = YamlConfiguration.loadConfiguration(multiplierFile);
        ConfigurationSection multSection = multYaml.getConfigurationSection("multipliers");
        if (multSection != null) for (String key : multSection.getKeys(false)) {
            tiers.add(new Tier(key, multYaml.getString("multipliers." + key + ".permission", ""), multYaml.getDouble("multipliers." + key + ".multiplier", 1.0)));
        }
        tiers.sort(Comparator.comparingDouble(Tier::multiplier).reversed());
    }

    long price(Material material) { return prices.getOrDefault(material, 0L); }
    List<Map.Entry<Material, Long>> sortedEntries() {
        List<Map.Entry<Material, Long>> list = new ArrayList<>(prices.entrySet());
        list.sort(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)));
        return list;
    }
    double multiplier(Player player) {
        for (Tier tier : tiers) if (tier.permission() != null && !tier.permission().isBlank() && player.hasPermission(tier.permission())) return tier.multiplier();
        for (Tier tier : tiers) if (tier.permission() == null || tier.permission().isBlank()) return tier.multiplier();
        return 1.0;
    }
}
