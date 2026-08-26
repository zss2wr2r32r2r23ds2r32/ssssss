package com.shardedcore.hook;

import com.shardedcore.ShardedCore;
import com.shardedcore.modules.crates.CratesModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.util.Amounts;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CoreExpansion extends PlaceholderExpansion {

    private final ShardedCore plugin;

    public CoreExpansion(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shardedcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ShardedMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        CratesModule crates = plugin.modules().get(CratesModule.class);
        String key = params.toLowerCase();
        return switch (key) {
            case "prefix" -> plugin.prefix();
            case "ping" -> String.valueOf(player.getPing());
            case "money", "balance", "bal", "lifestealcore_balance" ->
                    economy == null ? "0" : String.valueOf((long) economy.service().get(player.getUniqueId()));
            case "money_formatted", "balance_formatted", "bal_formatted",
                 "lifestealcore_balance_formatted" ->
                    economy == null ? "0" : economy.service().format(economy.service().get(player.getUniqueId()));
            case "money_commas", "balance_commas" ->
                    economy == null ? "0" : Amounts.commas(economy.service().get(player.getUniqueId()));
            case "kills" -> String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS));
            case "deaths" -> String.valueOf(player.getStatistic(Statistic.DEATHS));
            case "playtime" -> playtime(player);
            case "tag" -> {
                var tags = plugin.modules().get(com.shardedcore.modules.tags.TagsModule.class);
                yield tags == null ? "" : tags.display(player);
            }
            case "team" -> {
                var teams = plugin.modules().get(com.shardedcore.modules.teams.TeamsModule.class);
                yield teams == null ? "N/A" : teams.placeholder(player);
            }
            case "chatcolor" -> {
                var colors = plugin.modules().get(com.shardedcore.modules.chatcolor.ChatColorModule.class);
                yield colors == null ? "" : colors.display(player);
            }
            case "crystal", "crystals" -> {
                var crystals = plugin.modules().get(com.shardedcore.modules.crystals.CrystalsModule.class);
                yield crystals == null ? "0" : String.valueOf((long) crystals.service().get(player.getUniqueId()));
            }
            case "crystal_formated", "crystal_formatted", "crystals_formatted",
                 "crystals_formated" -> {
                var crystals = plugin.modules().get(com.shardedcore.modules.crystals.CrystalsModule.class);
                yield crystals == null ? "0" : crystals.service().format(crystals.service().get(player.getUniqueId()));
            }
            case "module_announce" -> plugin.modules().isEnabled("announce") ? "true" : "false";
            default -> {
                if (key.startsWith("module_")) {
                    yield plugin.modules().isEnabled(key.substring(7)) ? "enabled" : "disabled";
                }
                if (crates != null) {
                    if (key.startsWith("crate_keys_")) {
                        yield String.valueOf(crates.keys(player.getUniqueId(), key.substring("crate_keys_".length())));
                    }
                    if (key.startsWith("keys_")) {
                        yield String.valueOf(crates.keys(player.getUniqueId(), key.substring("keys_".length())));
                    }
                    if (key.startsWith("key_")) {
                        yield String.valueOf(crates.keys(player.getUniqueId(), key.substring("key_".length())));
                    }
                }
                yield null;
            }
        };
    }

    private static String playtime(Player player) {
        long seconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
