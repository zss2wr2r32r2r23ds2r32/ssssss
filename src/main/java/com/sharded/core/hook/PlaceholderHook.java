package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI expansions for tokens and killstreaks (used by Topper and holograms).
 *
 * <p>Topper example ({@code plugins/Topper/config.yml}):
 * <pre>
 * holders:
 *   token:
 *     type: placeholder
 *     placeholder: '%shardedcore_tokens%'
 *     online: true
 *
 * Hologram lines (holder MUST match config name exactly — use semicolons):
 *   %topper_token;top_name;1% &7| %topper_token;top_value;1%
 *   %topper_token;top_rank% &7| %playerpoints_points_shorthand%
 *
 * Direct fallback (no Topper):
 *   %shardedcore_token_top_1_name% %shardedcore_token_top_1_value%
 * </pre>
 */
public final class PlaceholderHook implements Listener {

    private final ShardedCore plugin;
    private boolean registered;

    public PlaceholderHook(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void tryRegister() {
        if (registered) return;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;

        new ShardedCoreExpansion().register();
        new TokenExpansion().register();
        new XKillstreakExpansion().register();
        new PlayerPointsExpansion().register();
        registered = true;
        plugin.getLogger().info("Registered PlaceholderAPI expansions (shardedcore, token, xkillstreak, playerpoints).");
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
            tryRegister();
        }
    }

    private final class ShardedCoreExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "shardedcore";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Sharded";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            String p = params.toLowerCase(Locale.ROOT);

            if (player != null) {
                TokensModule tokens = plugin.modules().get(TokensModule.class);
                if (tokens != null && tokens.service() != null) {
                    if (p.equals("tokens") || p.equals("tokens_amount")) {
                        return String.valueOf(tokens.service().getBalance(player.getUniqueId()));
                    }
                    if (p.equals("tokens_formatted")) {
                        return Numbers.format(tokens.service().getBalance(player.getUniqueId()));
                    }
                }

                KillstreakDatabase ks = killstreakDb();
                if (ks != null) {
                    if (p.equals("killstreak") || p.equals("killstreak_current")) {
                        return String.valueOf(ks.getCurrent(player.getUniqueId()));
                    }
                    if (p.equals("killstreak_best")) {
                        return String.valueOf(ks.getBest(player.getUniqueId()));
                    }
                }
            }

            if (p.startsWith("tokens_top_") || p.startsWith("token_top_")) {
                String spec = p.startsWith("tokens_top_")
                        ? p.substring("tokens_top_".length())
                        : p.substring("token_top_".length());
                return leaderboardValue(spec, true);
            }
            if (p.startsWith("killstreak_top_")) {
                return leaderboardValue(p.substring("killstreak_top_".length()), false);
            }
            return null;
        }
    }

    /** Alias expansion for Topper holder name {@code token}: {@code %token_top_1_name%}. */
    private final class TokenExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "token";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Sharded";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            String p = params.toLowerCase(Locale.ROOT);
            if (player != null) {
                TokensModule tokens = plugin.modules().get(TokensModule.class);
                if (tokens != null && tokens.service() != null) {
                    if (p.equals("amount") || p.equals("balance") || p.equals("tokens")) {
                        return String.valueOf(tokens.service().getBalance(player.getUniqueId()));
                    }
                    if (p.equals("formatted")) {
                        return Numbers.format(tokens.service().getBalance(player.getUniqueId()));
                    }
                }
            }
            if (p.startsWith("top_")) {
                return leaderboardValue(p.substring("top_".length()), true);
            }
            return null;
        }
    }

    /** Legacy-style placeholders: {@code %xkillstreak_current%}, {@code %xkillstreak_best%}. */
    private final class XKillstreakExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "xkillstreak";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Sharded";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            if (player == null) return "0";
            KillstreakDatabase ks = killstreakDb();
            if (ks == null) return "0";

            return switch (params.toLowerCase(Locale.ROOT)) {
                case "current", "streak" -> String.valueOf(ks.getCurrent(player.getUniqueId()));
                case "best", "max" -> String.valueOf(ks.getBest(player.getUniqueId()));
                default -> null;
            };
        }
    }

    /** PlayerPoints-compatible placeholders for holograms: {@code %playerpoints_points%}. */
    private final class PlayerPointsExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "playerpoints";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Sharded";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            if (player == null) return "0";
            TokensModule tokens = plugin.modules().get(TokensModule.class);
            if (tokens == null || tokens.service() == null) return "0";
            long balance = tokens.service().getBalance(player.getUniqueId());
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "points", "balance" -> String.valueOf(balance);
                case "points_formatted", "balance_formatted" -> Numbers.format(balance);
                case "points_shorthand" -> Numbers.format(balance);
                default -> null;
            };
        }
    }

    private KillstreakDatabase killstreakDb() {
        KillstreaksModule module = plugin.modules().get(KillstreaksModule.class);
        return module == null ? null : module.database();
    }

    private String leaderboardValue(String spec, boolean tokens) {
        String[] parts = spec.split("_", 2);
        if (parts.length == 0) return "";
        int rank;
        try {
            rank = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return "";
        }
        if (rank < 1 || rank > 10) return "";
        String field = parts.length > 1 ? parts[1] : "name";

        if (tokens) {
            TokensModule module = plugin.modules().get(TokensModule.class);
            if (module == null || module.database() == null) return "---";
            List<TokenDatabase.LeaderEntry> top = module.database().top(10);
            if (rank > top.size()) return field.equals("amount") || field.equals("value") ? "0" : "---";
            TokenDatabase.LeaderEntry entry = top.get(rank - 1);
            return switch (field) {
                case "amount", "value" -> String.valueOf(entry.value());
                case "formatted" -> Numbers.format(entry.value());
                default -> OfflinePlayers.name(entry.uuid());
            };
        }

        KillstreakDatabase ks = killstreakDb();
        if (ks == null) return "---";
        List<KillstreakDatabase.LeaderEntry> top = ks.topBest(10);
        if (rank > top.size()) return field.equals("amount") || field.equals("value") ? "0" : "---";
        KillstreakDatabase.LeaderEntry entry = top.get(rank - 1);
        return switch (field) {
            case "amount", "value" -> String.valueOf(entry.value());
            default -> OfflinePlayers.name(entry.uuid());
        };
    }
}
