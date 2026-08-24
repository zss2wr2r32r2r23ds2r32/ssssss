package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.leaderboards.LeaderboardsModule;
import com.sharded.core.modules.outpost.OutpostModule;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.teams.TeamDatabase;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.Text;
import com.sharded.core.util.TimeFormat;
import com.sharded.core.util.OfflinePlayers;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    private final List<PlaceholderExpansion> expansions = new ArrayList<>();

    public PlaceholderHook(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("PlaceholderAPI not found — placeholders disabled.");
            return;
        }
        for (PlaceholderExpansion expansion : expansions) {
            try {
                expansion.unregister();
            } catch (Exception ignored) {
            }
        }
        expansions.clear();

        expansions.add(new ShardedCoreExpansion());
        expansions.add(new TokenExpansion());
        expansions.add(new XKillstreakExpansion());
        expansions.add(new PlayerPointsExpansion());
        expansions.add(new KothExpansion());
        expansions.add(new OutpostExpansion());

        for (PlaceholderExpansion expansion : expansions) {
            if (expansion.register()) {
                plugin.getLogger().info("Registered PlaceholderAPI expansion: %" + expansion.getIdentifier() + "_%");
            }
        }
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

                TeamsModule teams = plugin.modules().get(TeamsModule.class);
                if (teams != null && teams.database() != null) {
                    if (p.equals("team") || p.equals("team_name") || p.equals("teamname")) {
                        Integer id = teams.database().getTeamId(player.getUniqueId());
                        if (id == null) {
                            var tm = plugin.modules().get(TeamsModule.class);
                            return tm == null ? "N/A" : tm.notInTeamPlaceholder();
                        }
                        TeamDatabase.Team team = teams.database().getTeamById(id);
                        return team == null ? teams.notInTeamPlaceholder() : team.name();
                    }
                }

                if (plugin.cosmetics() != null && plugin.cosmetics().database() != null) {
                    var cosmetics = plugin.cosmetics().database().get(player.getUniqueId());
                    if (p.equals("tag") || p.equals("tag_display")
                            || p.equals("eternaltags_tag") || p.equals("eternaltags_tag_display")) {
                        return cosmetics.tagDisplay() == null ? "" : ColorUtil.normalize(cosmetics.tagDisplay());
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
            if (p.equals("outpost_time") || p.equals("outpost_countdown")) {
                return outpostTime();
            }
            if (p.equals("koth_time") || p.equals("koth_countdown")) {
                return kothTime();
            }
            if (p.equals("outpost_active")) {
                OutpostModule outpost = plugin.modules().get(OutpostModule.class);
                return outpost != null && outpost.isActive() ? "true" : "false";
            }
            if (p.equals("koth_active")) {
                KothModule koth = plugin.modules().get(KothModule.class);
                return koth != null && koth.isActive() ? "true" : "false";
            }
            if (p.equals("outpost_capturer")) {
                OutpostModule outpost = plugin.modules().get(OutpostModule.class);
                return outpost == null ? "None" : outpost.capturerName();
            }
            if (p.equals("outpost_percent")) {
                OutpostModule outpost = plugin.modules().get(OutpostModule.class);
                return outpost == null ? "0" : String.format(Locale.US, "%.0f", outpost.capturePercent());
            }
            if (p.equals("koth_leader")) {
                KothModule koth = plugin.modules().get(KothModule.class);
                return koth == null ? "None" : koth.leaderName();
            }
            if (p.equals("koth_leader_points")) {
                KothModule koth = plugin.modules().get(KothModule.class);
                return koth == null ? "0" : String.format(Locale.US, "%.0f", koth.leaderPoints());
            }
            if (p.equals("outpost_hours") || p.equals("outpost_minutes") || p.equals("outpost_seconds")) {
                long ms = outpostMillis();
                return TimeFormat.replacePlaceholders("%" + p.substring("outpost_".length()) + "%", ms);
            }
            if (p.equals("koth_hours") || p.equals("koth_minutes") || p.equals("koth_seconds")) {
                long ms = kothMillis();
                return TimeFormat.replacePlaceholders("%" + p.substring("koth_".length()) + "%", ms);
            }
            return null;
        }
    }

    /** {@code %koth_time%}, {@code %koth_active%}, {@code %koth_leader%} */
    private final class KothExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "koth";
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
            KothModule koth = plugin.modules().get(KothModule.class);
            if (koth == null) return "";
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "time", "countdown" -> kothTime();
                case "active" -> koth.isActive() ? "true" : "false";
                case "leader" -> koth.leaderName();
                case "leader_points", "points" -> String.format(Locale.US, "%.0f", koth.leaderPoints());
                default -> null;
            };
        }
    }

    /** {@code %outpost_time%}, {@code %outpost_active%}, {@code %outpost_capturer%} */
    private final class OutpostExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "outpost";
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
            OutpostModule outpost = plugin.modules().get(OutpostModule.class);
            if (outpost == null) return "";
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "time", "countdown" -> outpostTime();
                case "active" -> outpost.isActive() ? "true" : "false";
                case "capturer", "capturing" -> outpost.capturerName();
                case "percent" -> String.format(Locale.US, "%.0f", outpost.capturePercent());
                default -> null;
            };
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

        if (field.equals("line") || field.equals("list")) {
            return topLine(rank, tokens);
        }

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

    private String outpostTime() {
        return TimeFormat.hms(outpostMillis());
    }

    private String kothTime() {
        return TimeFormat.hms(kothMillis());
    }

    private long outpostMillis() {
        OutpostModule outpost = plugin.modules().get(OutpostModule.class);
        return outpost == null ? 0 : outpost.millisUntilStart();
    }

    private long kothMillis() {
        KothModule koth = plugin.modules().get(KothModule.class);
        return koth == null ? 0 : koth.millisUntilStart();
    }

    private String topLine(int rank, boolean tokens) {
        LeaderboardsModule lb = plugin.modules().get(LeaderboardsModule.class);
        String template = lb == null
                ? "&a#%rank% &f%name% &7— &f%value% %label%"
                : lb.hologramLineTemplate(tokens ? "token" : "killstreak");
        String label = tokens ? "Tokens" : "Streak";
        if (tokens) {
            TokensModule module = plugin.modules().get(TokensModule.class);
            if (module == null || module.database() == null) return "---";
            List<TokenDatabase.LeaderEntry> top = module.database().top(10);
            if (rank > top.size()) return "---";
            TokenDatabase.LeaderEntry entry = top.get(rank - 1);
            return ColorUtil.normalize(template
                    .replace("%rank%", String.valueOf(rank))
                    .replace("%name%", OfflinePlayers.name(entry.uuid()))
                    .replace("%value%", Numbers.format(entry.value()))
                    .replace("%label%", label));
        }
        KillstreakDatabase ks = killstreakDb();
        if (ks == null) return "---";
        List<KillstreakDatabase.LeaderEntry> top = ks.topBest(10);
        if (rank > top.size()) return "---";
        KillstreakDatabase.LeaderEntry entry = top.get(rank - 1);
        return ColorUtil.normalize(template
                .replace("%rank%", String.valueOf(rank))
                .replace("%name%", OfflinePlayers.name(entry.uuid()))
                .replace("%value%", String.valueOf(entry.value()))
                .replace("%label%", label));
    }
}
